[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$ReportsDirectory = Join-Path $RepositoryRoot "reports/platform"
New-Item -ItemType Directory -Path $ReportsDirectory -Force | Out-Null

Push-Location $RepositoryRoot
try {
    & docker compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose validation failed." }
    & docker compose --file docker-compose.yml --file docker-compose.ci.yml config --quiet
    if ($LASTEXITCODE -ne 0) { throw "CI Docker Compose override validation failed." }

    $Python = if (Get-Command python3 -ErrorAction SilentlyContinue) { 'python3' } else { 'python' }
    & $Python ./scripts/gateway/render-config.py --check
    if ($LASTEXITCODE -ne 0) { throw 'Generated gateway provider configuration is stale.' }
    & helm lint ./deploy/helm/gateway-controller
    if ($LASTEXITCODE -ne 0) { throw 'Gateway controller Helm lint failed.' }
    & helm lint ./deploy/helm/gateway
    if ($LASTEXITCODE -ne 0) { throw 'Gateway routes Helm lint failed.' }
    $ControllerPath = Join-Path $ReportsDirectory 'gateway-controller-rendered.yaml'
    $GatewayPath = Join-Path $ReportsDirectory 'gateway-rendered.yaml'
    & helm template clinicflow-gateway-controller ./deploy/helm/gateway-controller --namespace ingress-system --api-versions monitoring.coreos.com/v1 | Set-Content $ControllerPath
    if ($LASTEXITCODE -ne 0) { throw 'Gateway controller render failed.' }
    & helm template clinicflow-gateway ./deploy/helm/gateway --namespace clinicflow | Set-Content $GatewayPath
    if ($LASTEXITCODE -ne 0) { throw 'Gateway routes render failed.' }
    & node ./scripts/gateway/validate-manifests.cjs $ControllerPath $GatewayPath
    if ($LASTEXITCODE -ne 0) { throw 'Pinned gateway CRD/schema/contract validation failed.' }

    $serviceReleases = [ordered]@{
        auth = "auth-service"
        patient = "patient-service"
        appointment = "appointment-service"
        clinical = "clinical-service"
        notification = "notification-service"
    }
    $renderedManifests = [System.Collections.Generic.List[string]]::new()
    $renderedManifests.Add($ControllerPath)
    $renderedManifests.Add($GatewayPath)

    & helm lint ./deploy/helm/observability --values ./deploy/helm/observability/values-docker-desktop.yaml
    if ($LASTEXITCODE -ne 0) { throw "Observability Helm lint failed." }
    $observabilityManifest = & helm template clinicflow-observability ./deploy/helm/observability --namespace monitoring --values ./deploy/helm/observability/values-docker-desktop.yaml
    if ($LASTEXITCODE -ne 0) { throw "Observability Helm rendering failed." }
    $observabilityPath = Join-Path $ReportsDirectory "observability-rendered.yaml"
    Set-Content -Path $observabilityPath -Value $observabilityManifest
    $renderedManifests.Add($observabilityPath)

    & helm lint ./deploy/helm/platform --values ./deploy/helm/platform/values-docker-desktop.yaml
    if ($LASTEXITCODE -ne 0) { throw "Platform Helm lint failed." }
    $platformManifest = & helm template clinicflow-platform ./deploy/helm/platform --namespace clinicflow --values ./deploy/helm/platform/values-docker-desktop.yaml
    if ($LASTEXITCODE -ne 0) { throw "Platform Helm rendering failed." }
    $platformPath = Join-Path $ReportsDirectory "platform-rendered.yaml"
    Set-Content -Path $platformPath -Value $platformManifest
    $renderedManifests.Add($platformPath)

    foreach ($service in $serviceReleases.GetEnumerator()) {
        $valuesPath = "./deploy/helm/services/$($service.Key).yaml"
        & helm lint ./deploy/helm/service --values $valuesPath
        if ($LASTEXITCODE -ne 0) { throw "$($service.Key) Helm lint failed." }

        $releaseName = "clinicflow-$($service.Key)"
        $manifest = & helm template $releaseName ./deploy/helm/service --namespace clinicflow --values $valuesPath
        if ($LASTEXITCODE -ne 0) { throw "$($service.Key) Helm rendering failed." }
        if ([regex]::Matches(($manifest -join "`n"), "(?m)^kind: Deployment$").Count -ne 1) {
            throw "$($service.Key) release must render exactly one Deployment."
        }
        if (($manifest -join "`n") -notmatch "(?ms)^kind: Deployment\s+metadata:\s+name: $([regex]::Escape($service.Value))\s") {
            throw "$($service.Key) release did not render Deployment '$($service.Value)'."
        }

        $manifestPath = Join-Path $ReportsDirectory "$($service.Key)-rendered.yaml"
        Set-Content -Path $manifestPath -Value $manifest
        $renderedManifests.Add($manifestPath)
    }

    & helm lint ./deploy/helm/frontend
    if ($LASTEXITCODE -ne 0) { throw "Frontend Helm lint failed." }
    $frontendManifest = & helm template clinicflow-frontend ./deploy/helm/frontend --namespace clinicflow
    if ($LASTEXITCODE -ne 0) { throw "Frontend Helm rendering failed." }
    if ([regex]::Matches(($frontendManifest -join "`n"), "(?m)^kind: Deployment$").Count -ne 1) {
        throw "Frontend release must render exactly one Deployment."
    }
    $frontendPath = Join-Path $ReportsDirectory "frontend-rendered.yaml"
    Set-Content -Path $frontendPath -Value $frontendManifest
    $renderedManifests.Add($frontendPath)

    & node ./scripts/gateway/validate-manifests.cjs @renderedManifests
    if ($LASTEXITCODE -ne 0) { throw 'Full gateway/network boundary validation failed.' }
    & helm template invalid ./deploy/helm/gateway-controller --set traefik.deployment.replicas=2 *> $null
    if ($LASTEXITCODE -eq 0) { throw 'Local quota schema accepted multiple proxy replicas.' }
    & helm template invalid ./deploy/helm/gateway --set rateLimits.public.average=0 *> $null
    if ($LASTEXITCODE -eq 0) { throw 'Gateway schema accepted a disabled public quota.' }
    & helm template invalid ./deploy/helm/frontend --set-string gatewayUrl= *> $null
    if ($LASTEXITCODE -eq 0) { throw 'Frontend schema accepted missing GATEWAY_URL.' }
    & helm template invalid ./deploy/helm/frontend --set-string oidc.issuer=clinicflow-auth *> $null
    if ($LASTEXITCODE -eq 0) { throw 'Frontend schema accepted an issuer that is not a realm URL.' }
    & helm template invalid ./deploy/helm/frontend --set-string oidc.publicUrl=https://clinicflow.localhost/app *> $null
    if ($LASTEXITCODE -eq 0) { throw 'Frontend schema accepted a public URL with a path.' }
    & helm template invalid ./deploy/helm/service --set-string image.tag=latest *> $null
    if ($LASTEXITCODE -eq 0) { throw "Service schema accepted mutable image tag 'latest'." }
    & helm template invalid ./deploy/helm/service --set-string image.tag= --set-string image.digest= *> $null
    if ($LASTEXITCODE -eq 0) { throw "Service schema accepted an empty image reference." }
    & helm template invalid ./deploy/helm/service --set jwt.enabled=true *> $null
    if ($LASTEXITCODE -eq 0) { throw "Service schema accepted a release that trusts two token issuers." }
    & helm template invalid ./deploy/helm/service --set oidc.enabled=false *> $null
    if ($LASTEXITCODE -eq 0) { throw "Service schema accepted a release that trusts no token issuer." }
    & helm template invalid ./deploy/helm/service --set-string oidc.issuer=clinicflow-auth *> $null
    if ($LASTEXITCODE -eq 0) { throw "Service schema accepted an issuer that is not a realm URL." }
    $digest = "sha256:" + ("a" * 64)
    & helm template invalid ./deploy/helm/service --set-string image.tag=immutable --set-string "image.digest=$digest" *> $null
    if ($LASTEXITCODE -eq 0) { throw "Service schema accepted both image tag and digest." }
    & helm template invalid ./deploy/helm/frontend --set-string image.tag=latest *> $null
    if ($LASTEXITCODE -eq 0) { throw "Frontend schema accepted mutable image tag 'latest'." }

    & kubeconform -strict -summary -ignore-missing-schemas @renderedManifests
    if ($LASTEXITCODE -ne 0) { throw "kubeconform validation failed." }
}
finally { Pop-Location }
