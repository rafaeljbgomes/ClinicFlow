[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$SkipObservability,
    [switch]$UpgradeGatewayCrds,
    [ValidateSet("All", "Platform", "Keycloak", "Patient", "Appointment", "Clinical", "Notification", "Frontend", "GatewayController", "Gateway")]
    [string]$Component = "All",
    [string]$ImageTag = "",
    [string]$Namespace = "clinicflow",
    [string]$MonitoringNamespace = "monitoring",
    [string]$IngressNamespace = "ingress-system",
    [string]$Timeout = "5m"
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$platformChart = Join-Path $repoRoot "deploy\helm\platform"
$platformValues = Join-Path $platformChart "values-docker-desktop.yaml"
$serviceChart = Join-Path $repoRoot "deploy\helm\service"
$serviceValuesDirectory = Join-Path $repoRoot "deploy\helm\services"
$frontendChart = Join-Path $repoRoot "deploy\helm\frontend"
$observabilityChart = Join-Path $repoRoot "deploy\helm\observability"
$observabilityValues = Join-Path $observabilityChart "values-docker-desktop.yaml"
$keycloakChart = Join-Path $repoRoot "deploy\helm\keycloak"
$keycloakRealm = Join-Path $repoRoot "docker\keycloak\realm\clinicflow-realm.json"

$components = [ordered]@{
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
    # auth-service was retired (ADR-031); this script no longer manages its release.
    & helm status clinicflow-auth --namespace $Namespace *> $null
    if ($LASTEXITCODE -eq 0) {
        Write-Warning "The retired 'clinicflow-auth' release is still installed. Remove it and its data with the steps in docs/development/local-development.md (Retiring auth-service)."
    }

    $selectedComponents = if ($Component -eq "All") {
        @($components.Keys)
    }
    elseif ($Component -in @("Platform", "Keycloak", "GatewayController", "Gateway")) {
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

    # Keycloak runs the Compose image, realm included, tagged by image ID in the same way (ADR-027).
    if ($Component -in @("All", "Keycloak")) {
        if (-not $SkipBuild) {
            Write-Host "Building the Keycloak image..."
            Invoke-Checked docker build --provenance=false -t clinicflow/keycloak:local docker/keycloak
        }
        $keycloakImageId = (& docker image inspect clinicflow/keycloak:local --format '{{.Id}}' 2>$null)
        if ($LASTEXITCODE -ne 0 -or "$keycloakImageId" -notmatch '^sha256:[0-9a-f]{64}$') {
            throw "Image 'clinicflow/keycloak:local' was not found; run without -SkipBuild to build it."
        }
        $keycloakImageTag = "id-$("$keycloakImageId".Substring(7, 12))"
        Invoke-Checked docker tag clinicflow/keycloak:local "clinicflow/keycloak:$keycloakImageTag"
    }

    Write-Host "Ensuring namespace '$Namespace' exists..."
    & kubectl get namespace $Namespace *> $null
    if ($LASTEXITCODE -ne 0) {
        Invoke-Checked kubectl create namespace $Namespace
    }

    # Keeps existing passwords and generates only missing ones; -Rotate on that script replaces them.
    & (Join-Path $PSScriptRoot "generate-dev-credentials.ps1")
    Write-Host "Creating or updating the platform administrator and per-service credential Secrets..."
    $credentialChecksums = @{
        postgres = Set-SecretFromEnvFile -Name clinicflow-postgres-admin -Path (Join-Path $repoRoot "secrets/platform/postgres.env")
        rabbitmq = Set-SecretFromEnvFile -Name clinicflow-rabbitmq-admin -Path (Join-Path $repoRoot "secrets/platform/rabbitmq.env")
    }
    # The BFF's Keycloak client secret (ADR-027), the same generated value the realm is imported with.
    $credentialChecksums.keycloakClient = Set-SecretFromEnvFile -Name clinicflow-bff-oidc -Path (Join-Path $repoRoot "secrets/keycloak/clinicflow-bff.env")
    # The BFF's read-only Admin API client for the administrator's user lookup (ADR-031).
    $credentialChecksums.userLookupClient = Set-SecretFromEnvFile -Name clinicflow-user-lookup-oidc -Path (Join-Path $repoRoot "secrets/keycloak/clinicflow-user-lookup.env")
    # Keycloak's database login, applied by the platform bootstrap and used by Keycloak itself.
    $credentialChecksums.keycloakDatabase = Set-SecretFromEnvFile -Name clinicflow-keycloak-database -Path (Join-Path $repoRoot "secrets/keycloak/database.env")
    # The master realm's bootstrap administrator, applied only when Keycloak creates its database.
    $credentialChecksums.keycloakAdmin = Set-SecretFromEnvFile -Name clinicflow-keycloak-admin -Path (Join-Path $repoRoot "secrets/keycloak/admin.env")
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

    if ($Component -in @("All", "Keycloak")) {
        # Keycloak imports the realm only when it does not exist yet. The release records the realm file it
        # was first installed with, and scripts/reset-keycloak-realm.ps1 records each re-import, so a changed
        # file is reported here. Resetting is left to the operator: it removes self-registered users.
        $realmChecksum = (Get-FileHash -Algorithm SHA256 -LiteralPath $keycloakRealm).Hash.ToLowerInvariant()
        $importedRealm = $realmChecksum
        $installedValues = & helm get values clinicflow-keycloak --namespace $Namespace --all -o json 2>$null
        if ($LASTEXITCODE -eq 0 -and $installedValues) {
            $installedRealm = ($installedValues | ConvertFrom-Json).global.clinicflow.checksums.realm
            if ($installedRealm) { $importedRealm = $installedRealm }
            if ($installedRealm -and $installedRealm -ne $realmChecksum) {
                Write-Warning "docker/keycloak/realm/clinicflow-realm.json changed since Keycloak imported it. Run scripts/reset-keycloak-realm.ps1 -Target Kubernetes to apply it; that removes self-registered users."
            }
        }

        # The realm's synthetic demo password, overridable as in Compose (docs/development/local-development.md).
        $demoPassword = $env:CLINICFLOW_DEMO_PASSWORD ?? 'ClinicFlowDemo!2026'

        Write-Host "Installing or upgrading Keycloak..."
        Invoke-Checked helm upgrade --install clinicflow-keycloak $keycloakChart `
            --namespace $Namespace `
            --set-string "keycloakx.image.tag=$keycloakImageTag" `
            --set-string "global.clinicflow.checksums.database=$($credentialChecksums.keycloakDatabase)" `
            --set-string "global.clinicflow.checksums.admin=$($credentialChecksums.keycloakAdmin)" `
            --set-string "global.clinicflow.checksums.client=$($credentialChecksums.keycloakClient)" `
            --set-string "global.clinicflow.checksums.userLookup=$($credentialChecksums.userLookupClient)" `
            --set-string "global.clinicflow.checksums.realm=$importedRealm" `
            --set-string "global.clinicflow.demoPassword=$demoPassword" `
            --set-string "networkPolicy.gatewayNamespace=$IngressNamespace" `
            --set-string "networkPolicy.monitoringNamespace=$MonitoringNamespace" `
            --set "keycloakx.serviceMonitor.enabled=$((-not $SkipObservability).ToString().ToLowerInvariant())" `
            --rollback-on-failure `
            --wait=watcher `
            --server-side=false `
            --timeout $Timeout
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
            $arguments += @('--set-string',"gatewayNamespace=$IngressNamespace",'--set-string',"gatewayUrl=http://clinicflow-gateway-api.$IngressNamespace.svc.cluster.local:8080",'--set-string',"oidc.clientSecretChecksum=$($credentialChecksums.keycloakClient)",'--set-string',"oidc.userLookupSecretChecksum=$($credentialChecksums.userLookupClient)")
        }
        Write-Host "Installing or upgrading $($definition.Release)..."
        Invoke-Checked helm @arguments
    }

    if ($Component -in @('All','Gateway')) {
        # The identity routes point at Keycloak's Service; without it they never resolve their references.
        $KeycloakService = & kubectl get service keycloak --namespace $Namespace --ignore-not-found -o name
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the Keycloak Service.' }
        if (-not $KeycloakService) { throw "Service 'keycloak' is missing in namespace '$Namespace'; deploy -Component Keycloak before the gateway." }

        # Issues the certificate, or a new leaf when it lacks a gateway host, then keeps the Secret in step.
        & (Join-Path $PSScriptRoot 'gateway/New-DevelopmentCertificate.ps1')
        $Certificate = Join-Path $repoRoot 'secrets/edge/edge.crt'
        $CertificateKey = Join-Path $repoRoot 'secrets/edge/edge.key'
        $InstalledCertificate = & kubectl get secret clinicflow-edge-tls --namespace $Namespace --ignore-not-found -o 'jsonpath={.data.tls\.crt}'
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect development TLS Secret.' }
        if ("$InstalledCertificate" -ne [Convert]::ToBase64String([IO.File]::ReadAllBytes($Certificate))) {
            Write-Host 'Creating or updating the development TLS Secret...'
            $TlsManifest = & kubectl create secret tls clinicflow-edge-tls --namespace $Namespace "--cert=$Certificate" "--key=$CertificateKey" --dry-run=client -o yaml
            if ($LASTEXITCODE -ne 0) { throw 'Failed to generate the development TLS Secret manifest.' }
            $TlsManifest | & kubectl apply -f - | Out-Null
            if ($LASTEXITCODE -ne 0) { throw 'Failed to apply the development TLS Secret.' }
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
    if ($Component -in @("All", "Keycloak")) {
        Write-Host "Open the Keycloak admin console with:"
        Write-Host "  kubectl port-forward svc/keycloak 8180:8080 -n $Namespace, then http://localhost:8180/admin/"
    }
    if (-not $SkipObservability -and $Component -in @("All", "Platform")) {
        Write-Host "Open Grafana with:"
        Write-Host "  kubectl port-forward svc/clinicflow-observability-grafana 3001:80 -n $MonitoringNamespace"
    }
}
finally {
    Pop-Location
}
