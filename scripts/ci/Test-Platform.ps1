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
    & helm lint ./deploy/helm/clinicflow
    if ($LASTEXITCODE -ne 0) { throw "Helm lint failed." }

    $RenderedDirectory = Join-Path $ReportsDirectory "rendered"
    $RenderedManifest = Join-Path $ReportsDirectory "clinicflow-rendered.yaml"
    & helm template clinicflow ./deploy/helm/clinicflow --output-dir $RenderedDirectory
    if ($LASTEXITCODE -ne 0) { throw "Helm template rendering failed." }
    Get-ChildItem -Path $RenderedDirectory -Recurse -Filter *.yaml | Get-Content | Set-Content -Path $RenderedManifest
    & kubeconform -strict -summary -ignore-missing-schemas $RenderedManifest
    if ($LASTEXITCODE -ne 0) { throw "kubeconform validation failed." }
}
finally { Pop-Location }
