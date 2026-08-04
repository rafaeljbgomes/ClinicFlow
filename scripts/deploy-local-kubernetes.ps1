[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$SkipObservability,
    [string]$ImageTag = "0.1.0-local",
    [string]$Namespace = "clinicflow",
    [string]$MonitoringNamespace = "monitoring",
    [string]$Timeout = "5m"
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$privateKey = Join-Path $repoRoot "secrets\jwt-private.pem"
$publicKey = Join-Path $repoRoot "secrets\jwt-public.pem"
$applicationChart = Join-Path $repoRoot "deploy\helm\clinicflow"
$applicationValues = Join-Path $applicationChart "values-docker-desktop.yaml"
$observabilityChart = Join-Path $repoRoot "deploy\helm\observability"
$observabilityValues = Join-Path $observabilityChart "values-docker-desktop.yaml"

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

    if (-not $SkipBuild) {
        Write-Host "Building ClinicFlow images with tag '$ImageTag'..."
        $images = @(
            @{ Name = "auth-service"; Dockerfile = "auth-service/Dockerfile"; Context = "." },
            @{ Name = "patient-service"; Dockerfile = "patient-service/Dockerfile"; Context = "." },
            @{ Name = "appointment-service"; Dockerfile = "appointment-service/Dockerfile"; Context = "." },
            @{ Name = "clinical-service"; Dockerfile = "clinical-service/Dockerfile"; Context = "." },
            @{ Name = "notification-service"; Dockerfile = "notification-service/Dockerfile"; Context = "." },
            @{ Name = "frontend"; Dockerfile = "frontend/Dockerfile"; Context = "frontend" }
        )

        foreach ($image in $images) {
            Invoke-Checked docker build `
                -t "clinicflow/$($image.Name):$ImageTag" `
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

    if (-not $SkipObservability) {
        Write-Host "Installing or upgrading observability..."
        Invoke-Checked helm dependency update $observabilityChart
        Invoke-Checked helm upgrade --install clinicflow-observability $observabilityChart `
            --namespace $MonitoringNamespace `
            --create-namespace `
            --values $observabilityValues `
            --wait `
            --timeout $Timeout
    }

    Write-Host "Installing or upgrading ClinicFlow..."
    Invoke-Checked helm upgrade --install clinicflow $applicationChart `
        --namespace $Namespace `
        --values $applicationValues `
        --set "global.imageTag=$ImageTag" `
        --wait `
        --timeout $Timeout

    Write-Host ""
    Write-Host "ClinicFlow deployment completed."
    Invoke-Checked kubectl get pods --namespace $Namespace
    Write-Host ""
    Write-Host "Open the frontend with:"
    Write-Host "  kubectl port-forward svc/frontend 3000:3000 -n $Namespace"
    if (-not $SkipObservability) {
        Write-Host "Open Grafana with:"
        Write-Host "  kubectl port-forward svc/clinicflow-observability-grafana 3001:80 -n $MonitoringNamespace"
    }
}
finally {
    Pop-Location
}
