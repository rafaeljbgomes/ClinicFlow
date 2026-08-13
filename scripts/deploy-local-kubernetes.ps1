[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$SkipObservability,
    [ValidateSet("All", "Platform", "Auth", "Patient", "Appointment", "Clinical", "Notification", "Frontend")]
    [string]$Component = "All",
    [string]$ImageTag = "",
    [string]$Namespace = "clinicflow",
    [string]$MonitoringNamespace = "monitoring",
    [string]$Timeout = "5m"
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$privateKey = Join-Path $repoRoot "secrets\jwt-private.pem"
$publicKey = Join-Path $repoRoot "secrets\jwt-public.pem"
$platformChart = Join-Path $repoRoot "deploy\helm\platform"
$platformValues = Join-Path $platformChart "values-docker-desktop.yaml"
$serviceChart = Join-Path $repoRoot "deploy\helm\service"
$serviceValuesDirectory = Join-Path $repoRoot "deploy\helm\services"
$frontendChart = Join-Path $repoRoot "deploy\helm\frontend"
$observabilityChart = Join-Path $repoRoot "deploy\helm\observability"
$observabilityValues = Join-Path $observabilityChart "values-docker-desktop.yaml"

$components = [ordered]@{
    Auth = @{ Release = "clinicflow-auth"; Image = "auth-service"; Dockerfile = "auth-service/Dockerfile"; Context = "."; Values = "auth.yaml"; Chart = $serviceChart }
    Patient = @{ Release = "clinicflow-patient"; Image = "patient-service"; Dockerfile = "patient-service/Dockerfile"; Context = "."; Values = "patient.yaml"; Chart = $serviceChart }
    Appointment = @{ Release = "clinicflow-appointment"; Image = "appointment-service"; Dockerfile = "appointment-service/Dockerfile"; Context = "."; Values = "appointment.yaml"; Chart = $serviceChart }
    Clinical = @{ Release = "clinicflow-clinical"; Image = "clinical-service"; Dockerfile = "clinical-service/Dockerfile"; Context = "."; Values = "clinical.yaml"; Chart = $serviceChart }
    Notification = @{ Release = "clinicflow-notification"; Image = "notification-service"; Dockerfile = "notification-service/Dockerfile"; Context = "."; Values = "notification.yaml"; Chart = $serviceChart }
    Frontend = @{ Release = "clinicflow-frontend"; Image = "frontend"; Dockerfile = "frontend/Dockerfile"; Context = "frontend"; Values = $null; Chart = $frontendChart }
}

function Assert-Command {
    param([Parameter(Mandatory)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' was not found in PATH."
    }
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory)][string]$Command,
        [Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments
    )

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $Command $($Arguments -join ' ')"
    }
}

Push-Location $repoRoot
try {
    Assert-Command docker
    Assert-Command kubectl
    Assert-Command helm

    Write-Host "Checking Kubernetes cluster..."
    Invoke-Checked kubectl cluster-info

    & helm status clinicflow --namespace $Namespace *> $null
    if ($LASTEXITCODE -eq 0) {
        throw "Legacy Helm release 'clinicflow' is installed in namespace '$Namespace'. Uninstall it before using independently managed releases."
    }

    $selectedComponents = if ($Component -eq "All") {
        @($components.Keys)
    }
    elseif ($Component -eq "Platform") {
        @()
    }
    else {
        @($Component)
    }

    if ($selectedComponents.Count -gt 0 -and [string]::IsNullOrWhiteSpace($ImageTag)) {
        if ($SkipBuild) {
            throw "-ImageTag is required when -SkipBuild is used for an application component."
        }

        $commit = (& git rev-parse --short=12 HEAD).Trim()
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($commit)) {
            throw "Unable to derive an immutable image tag from the current Git commit. Pass -ImageTag explicitly."
        }
        $ImageTag = "git-$commit-$(Get-Date -AsUTC -Format 'yyyyMMddHHmmss')"
    }

    if (-not $SkipBuild) {
        foreach ($componentName in $selectedComponents) {
            $image = $components[$componentName]
            Write-Host "Building $componentName image with tag '$ImageTag'..."
            Invoke-Checked docker build `
                -t "clinicflow/$($image.Image):$ImageTag" `
                -f $image.Dockerfile `
                $image.Context
        }
    }

    if (-not (Test-Path $privateKey) -or -not (Test-Path $publicKey)) {
        Write-Host "JWT development keys are missing; generating them..."
        & (Join-Path $PSScriptRoot "generate-dev-jwt-keys.ps1")
    }

    Write-Host "Ensuring namespace '$Namespace' exists..."
    & kubectl get namespace $Namespace *> $null
    if ($LASTEXITCODE -ne 0) {
        Invoke-Checked kubectl create namespace $Namespace
    }

    Write-Host "Creating or updating the ClinicFlow JWT secret..."
    $secretManifest = & kubectl create secret generic clinicflow-jwt `
        "--from-file=jwt-private.pem=$privateKey" `
        "--from-file=jwt-public.pem=$publicKey" `
        --namespace $Namespace `
        --dry-run=client `
        -o yaml
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to generate the clinicflow-jwt Secret manifest."
    }
    $secretManifest | & kubectl apply -f -
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to apply the clinicflow-jwt Secret."
    }

    if (-not $SkipObservability -and $Component -in @("All", "Platform")) {
        Write-Host "Installing or upgrading observability..."
        Invoke-Checked helm dependency update $observabilityChart
        Invoke-Checked helm upgrade --install clinicflow-observability $observabilityChart `
            --namespace $MonitoringNamespace `
            --create-namespace `
            --values $observabilityValues `
            --wait `
            --timeout $Timeout
    }

    if ($Component -in @("All", "Platform")) {
        Write-Host "Installing or upgrading shared ClinicFlow platform resources..."
        Invoke-Checked helm upgrade --install clinicflow-platform $platformChart `
            --namespace $Namespace `
            --values $platformValues `
            --atomic `
            --wait `
            --timeout $Timeout
    }
    else {
        & helm status clinicflow-platform --namespace $Namespace *> $null
        if ($LASTEXITCODE -ne 0) {
            throw "Platform release 'clinicflow-platform' must be installed before upgrading $Component."
        }
    }

    foreach ($componentName in $selectedComponents) {
        $definition = $components[$componentName]
        $arguments = @(
            "upgrade", "--install", $definition.Release, $definition.Chart,
            "--namespace", $Namespace,
            "--set-string", "image.tag=$ImageTag",
            "--atomic", "--wait", "--timeout", $Timeout
        )
        if ($definition.Values) {
            $arguments += @("--values", (Join-Path $serviceValuesDirectory $definition.Values))
        }

        Write-Host "Installing or upgrading $($definition.Release)..."
        Invoke-Checked helm @arguments
    }

    Write-Host ""
    Write-Host "ClinicFlow $Component deployment completed."
    Invoke-Checked helm list --namespace $Namespace
    Invoke-Checked kubectl get pods --namespace $Namespace
    Write-Host ""
    Write-Host "Open the frontend with:"
    Write-Host "  kubectl port-forward svc/frontend 3000:3000 -n $Namespace"
    if (-not $SkipObservability -and $Component -in @("All", "Platform")) {
        Write-Host "Open Grafana with:"
        Write-Host "  kubectl port-forward svc/clinicflow-observability-grafana 3001:80 -n $MonitoringNamespace"
    }
}
finally {
    Pop-Location
}
