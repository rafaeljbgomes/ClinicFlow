[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$SkipObservability,
    [switch]$UpgradeGatewayCrds,
    [ValidateSet("All", "Platform", "Auth", "Patient", "Appointment", "Clinical", "Notification", "Frontend", "GatewayController", "Gateway")]
    [string]$Component = "All",
    [string]$ImageTag = "",
    [string]$Namespace = "clinicflow",
    [string]$MonitoringNamespace = "monitoring",
    [string]$IngressNamespace = "ingress-system",
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

function Assert-HelmCompatibility {
    $version = (& helm version --template '{{ .Version }}').Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to determine the Helm version."
    }
    if ($version -notmatch '^v?4\.') {
        throw "ClinicFlow local Kubernetes deployment requires Helm 4.x; found '$version'. Jenkins pins the validated patch in deploy/jenkins/agent/Dockerfile."
    }
    Write-Host "Using supported Helm version $version."
}

function Set-SecretFromEnvFile {
    # Creates or updates a Secret from a generated env file and returns the file's SHA-256, which the
    # charts put on the pod template so a rotated password rolls the pods (ADR-026).
    param([Parameter(Mandatory)][string]$Name, [Parameter(Mandatory)][string]$Path)

    $manifest = & kubectl create secret generic $Name "--from-env-file=$Path" --namespace $Namespace --dry-run=client -o yaml
    if ($LASTEXITCODE -ne 0) { throw "Failed to generate the $Name Secret manifest." }
    $manifest | & kubectl apply -f - | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Failed to apply the $Name Secret." }
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Invoke-Checked {
    # A simple function keeps native flags such as kubectl's -o out of
    # PowerShell's common-parameter binding (OutVariable/OutBuffer).
    param([string]$Command)
    $Arguments = $args

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
    Assert-HelmCompatibility

    Write-Host "Checking Kubernetes cluster..."
    Invoke-Checked kubectl cluster-info
    if ($Component -in @('All','GatewayController','Gateway')) {
        Invoke-Checked kubectl version -o json
        $AdmissionApi = & kubectl api-resources --api-group=admissionregistration.k8s.io -o name
        if ($LASTEXITCODE -ne 0 -or $AdmissionApi -notcontains 'validatingadmissionpolicies.admissionregistration.k8s.io') { throw 'The pinned Gateway API bundle requires ValidatingAdmissionPolicy support.' }
        Invoke-Checked kubectl get daemonsets -A
        Write-Host 'Gateway preflight: inspect CNI enforcement and LoadBalancer support; isolation acceptance requires live probes.'
        foreach ($Port in @(80,443)) {
            $Client = [Net.Sockets.TcpClient]::new()
            try {
                if ($Client.ConnectAsync('127.0.0.1',$Port).Wait(300) -and $Client.Connected) {
                    $ExistingGateway = & kubectl get service clinicflow-gateway-controller -n $IngressNamespace --ignore-not-found -o name
                    if ($LASTEXITCODE -ne 0 -or -not $ExistingGateway) { throw "Port $Port is occupied without an existing gateway Service; inspect its owner before deployment." }
                    Write-Warning "Port $Port is already listening; confirm it belongs to the gateway LoadBalancer."
                }
            } finally { $Client.Dispose() }
        }
    }

    & helm status clinicflow --namespace $Namespace *> $null
    if ($LASTEXITCODE -eq 0) {
        throw "Legacy Helm release 'clinicflow' is installed in namespace '$Namespace'. Uninstall it before using independently managed releases."
    }

    $selectedComponents = if ($Component -eq "All") {
        @($components.Keys)
    }
    elseif ($Component -in @("Platform", "GatewayController", "Gateway")) {
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

    # The platform runs ClinicFlow's PostgreSQL image without gosu (ADR-028). It is tagged by image ID,
    # so a rebuild that changes nothing keeps the tag and does not restart the database. Provenance is
    # off because its timestamped attestation would change the ID of every build.
    if ($Component -in @("All", "Platform")) {
        if (-not $SkipBuild) {
            Write-Host "Building the PostgreSQL image..."
            Invoke-Checked docker build --provenance=false --target server -t clinicflow/postgres:local docker/postgres
        }
        $postgresImageId = (& docker image inspect clinicflow/postgres:local --format '{{.Id}}' 2>$null)
        if ($LASTEXITCODE -ne 0 -or "$postgresImageId" -notmatch '^sha256:[0-9a-f]{64}$') {
            throw "Image 'clinicflow/postgres:local' was not found; run without -SkipBuild to build it."
        }
        $postgresImage = "clinicflow/postgres:id-$("$postgresImageId".Substring(7, 12))"
        Invoke-Checked docker tag clinicflow/postgres:local $postgresImage
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

    # Keeps existing passwords and generates only missing ones; -Rotate on that script replaces them.
    & (Join-Path $PSScriptRoot "generate-dev-credentials.ps1")
    Write-Host "Creating or updating the platform administrator and per-service credential Secrets..."
    $credentialChecksums = @{
        postgres = Set-SecretFromEnvFile -Name clinicflow-postgres-admin -Path (Join-Path $repoRoot "secrets/platform/postgres.env")
        rabbitmq = Set-SecretFromEnvFile -Name clinicflow-rabbitmq-admin -Path (Join-Path $repoRoot "secrets/platform/rabbitmq.env")
    }
    foreach ($credentialFile in Get-ChildItem (Join-Path $repoRoot "secrets/services") -Filter "*.env") {
        $credentialChecksums[$credentialFile.BaseName] = Set-SecretFromEnvFile -Name "$($credentialFile.BaseName)-credentials" -Path $credentialFile.FullName
    }

    if (-not $SkipObservability -and $Component -in @("All", "Platform")) {
        Write-Host "Installing or upgrading observability..."
        Invoke-Checked helm dependency update $observabilityChart
        Invoke-Checked helm upgrade --install clinicflow-observability $observabilityChart `
            --namespace $MonitoringNamespace `
            --create-namespace `
            --values $observabilityValues `
            --rollback-on-failure `
            --wait=legacy `
            --server-side=false `
            --timeout $Timeout
    }

    if ($Component -in @("All", "Platform")) {
        Write-Host "Installing or upgrading shared ClinicFlow platform resources..."
        Invoke-Checked helm upgrade --install clinicflow-platform $platformChart `
            --namespace $Namespace `
            --values $platformValues `
            --set-string "postgres.image=$postgresImage" `
            --set-string "postgres.credentialsChecksum=$($credentialChecksums.postgres)" `
            --set-string "rabbitmq.credentialsChecksum=$($credentialChecksums.rabbitmq)" `
            --rollback-on-failure `
            --wait=watcher `
            --server-side=false `
            --timeout $Timeout
    }
    else {
        & helm status clinicflow-platform --namespace $Namespace *> $null
        if ($LASTEXITCODE -ne 0) {
            throw "Platform release 'clinicflow-platform' must be installed before upgrading $Component."
        }
    }

    if ($Component -in @('All','GatewayController')) {
        & (Join-Path $PSScriptRoot 'gateway/Initialize-GatewayCrds.ps1') -Upgrade:$UpgradeGatewayCrds
        $ApiService = (& kubectl get service kubernetes -n default -o json) | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0) { throw 'Cannot discover Kubernetes API Service.' }
        $ApiEndpoints = (& kubectl get endpointslices -n default -l kubernetes.io/service-name=kubernetes -o json) | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0) { throw 'Cannot discover Kubernetes API endpoints.' }
        $ApiPorts = @((@($ApiService.spec.ports.port) + @($ApiEndpoints.items.ports.port)) | Where-Object { $_ } | Sort-Object -Unique)
        if (-not $ApiPorts.Count) { throw 'No Kubernetes API ports discovered.' }
        $Addresses = @($ApiService.spec.clusterIPs) + @($ApiEndpoints.items.endpoints.addresses)
        $Cidrs = @($Addresses | Where-Object { $_ } | Sort-Object -Unique | ForEach-Object {
            $Ip = [Net.IPAddress]::Parse($_)
            if ($Ip.AddressFamily -eq [Net.Sockets.AddressFamily]::InterNetwork) { "$($_)/32" } else { "$($_)/128" }
        })
        if (-not $Cidrs.Count) { throw 'No Kubernetes API CIDRs discovered; refusing unrestricted controller egress.' }
        $ControllerChart = Join-Path $repoRoot 'deploy/helm/gateway-controller'
        $ControllerValues = @{ applicationNamespace=$Namespace; monitoringNamespace=$MonitoringNamespace; kubernetesApi=@{cidrs=$Cidrs;ports=$ApiPorts}; traefik=@{providers=@{kubernetesGateway=@{namespaces=@($Namespace,$IngressNamespace);statusAddress=@{service=@{namespace=$IngressNamespace}}};kubernetesCRD=@{namespaces=@($Namespace,$IngressNamespace)}};metrics=@{prometheus=@{serviceMonitor=@{enabled=(-not $SkipObservability)}}}} }
        $TemporaryValues = Join-Path ([IO.Path]::GetTempPath()) "clinicflow-gateway-$([Guid]::NewGuid()).json"
        try {
            $ControllerValues | ConvertTo-Json -Depth 10 | Set-Content $TemporaryValues
            Invoke-Checked helm dependency build $ControllerChart
            Invoke-Checked helm upgrade --install clinicflow-gateway-controller $ControllerChart --namespace $IngressNamespace --create-namespace --values $TemporaryValues --skip-crds --rollback-on-failure --wait=watcher --server-side=false --timeout $Timeout
        }
        finally { Remove-Item -LiteralPath $TemporaryValues -ErrorAction SilentlyContinue }
    }

    foreach ($componentName in $selectedComponents) {
        $definition = $components[$componentName]
        $arguments = @(
            "upgrade", "--install", $definition.Release, $definition.Chart,
            "--namespace", $Namespace,
            "--set-string", "image.tag=$ImageTag",
            "--rollback-on-failure", "--wait=watcher", "--server-side=false", "--timeout", $Timeout
        )
        if ($definition.Values) {
            $arguments += @("--values", (Join-Path $serviceValuesDirectory $definition.Values), "--set-string", "networkPolicy.gatewayNamespace=$IngressNamespace", "--set-string", "networkPolicy.monitoringNamespace=$MonitoringNamespace")
            $arguments += @("--set-string", "credentials.checksum=$($credentialChecksums[$definition.Image])")
        }

        if ($componentName -eq 'Frontend') {
            $arguments += @('--set-string',"gatewayNamespace=$IngressNamespace",'--set-string',"gatewayUrl=http://clinicflow-gateway-api.$IngressNamespace.svc.cluster.local:8080")
        }
        Write-Host "Installing or upgrading $($definition.Release)..."
        Invoke-Checked helm @arguments
    }

    if ($Component -in @('All','Gateway')) {
        $TlsSecret = & kubectl get secret clinicflow-edge-tls --namespace $Namespace --ignore-not-found -o name
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect development TLS Secret.' }
        if (-not $TlsSecret) {
            & (Join-Path $PSScriptRoot 'gateway/New-DevelopmentCertificate.ps1')
            $Certificate = Join-Path $repoRoot 'secrets/edge/edge.crt'
            $CertificateKey = Join-Path $repoRoot 'secrets/edge/edge.key'
            Invoke-Checked kubectl create secret tls clinicflow-edge-tls --namespace $Namespace "--cert=$Certificate" "--key=$CertificateKey"
        }
        Invoke-Checked helm upgrade --install clinicflow-gateway (Join-Path $repoRoot 'deploy/helm/gateway') --namespace $Namespace --rollback-on-failure --wait=watcher --server-side=false --timeout $Timeout
        & (Join-Path $PSScriptRoot 'gateway/Wait-GatewayRoutes.ps1') -Namespace $Namespace
        & (Join-Path $PSScriptRoot 'gateway/Test-GatewayTraffic.ps1') -IngressNamespace $IngressNamespace
    }
    Write-Host ""
    Write-Host "ClinicFlow $Component deployment completed."
    Invoke-Checked helm list --namespace $Namespace
    Invoke-Checked kubectl get pods --namespace $Namespace
    Write-Host ""
    Write-Host "Open the frontend with:"
    Write-Host "  https://clinicflow.localhost (trust secrets/edge/ca.crt manually)"
    if (-not $SkipObservability -and $Component -in @("All", "Platform")) {
        Write-Host "Open Grafana with:"
        Write-Host "  kubectl port-forward svc/clinicflow-observability-grafana 3001:80 -n $MonitoringNamespace"
    }
}
finally {
    Pop-Location
}
