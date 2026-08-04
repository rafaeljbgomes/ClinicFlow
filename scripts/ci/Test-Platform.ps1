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
    & helm lint ./deploy/helm/clinicflow
    if ($LASTEXITCODE -ne 0) { throw "Helm lint failed." }

    $RenderedManifest = Join-Path $ReportsDirectory "clinicflow-rendered.yaml"
    $RenderedManifestContent = & helm template clinicflow ./deploy/helm/clinicflow
    if ($LASTEXITCODE -ne 0) { throw "Helm template rendering failed." }
    Set-Content -Path $RenderedManifest -Value $RenderedManifestContent
    & kubeconform -strict -summary -ignore-missing-schemas $RenderedManifest
    if ($LASTEXITCODE -ne 0) { throw "kubeconform validation failed." }
}
finally { Pop-Location }
