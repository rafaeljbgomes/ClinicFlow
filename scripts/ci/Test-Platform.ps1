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

    $serviceReleases = [ordered]@{
        auth = "auth-service"
        patient = "patient-service"
        appointment = "appointment-service"
        clinical = "clinical-service"
        notification = "notification-service"
    }
    $renderedManifests = [System.Collections.Generic.List[string]]::new()

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

    & helm template invalid ./deploy/helm/service --set-string image.tag=latest *> $null
    if ($LASTEXITCODE -eq 0) { throw "Service schema accepted mutable image tag 'latest'." }
    & helm template invalid ./deploy/helm/service --set-string image.tag= --set-string image.digest= *> $null
    if ($LASTEXITCODE -eq 0) { throw "Service schema accepted an empty image reference." }
    $digest = "sha256:" + ("a" * 64)
    & helm template invalid ./deploy/helm/service --set-string image.tag=immutable --set-string "image.digest=$digest" *> $null
    if ($LASTEXITCODE -eq 0) { throw "Service schema accepted both image tag and digest." }
    & helm template invalid ./deploy/helm/frontend --set-string image.tag=latest *> $null
    if ($LASTEXITCODE -eq 0) { throw "Frontend schema accepted mutable image tag 'latest'." }

    & kubeconform -strict -summary -ignore-missing-schemas @renderedManifests
    if ($LASTEXITCODE -ne 0) { throw "kubeconform validation failed." }
}
finally { Pop-Location }
