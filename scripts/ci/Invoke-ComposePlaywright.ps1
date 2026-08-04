[CmdletBinding()]
param([Parameter(Mandatory)][string]$RunId)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "Compose-Project.ps1")
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$ProjectName = ConvertTo-ComposeProjectName -RunId $RunId
Set-IsolatedPorts -RunId $RunId

Push-Location $RepositoryRoot
try {
    if (-not (Test-Path -LiteralPath "secrets/jwt-private.pem")) {
        & ./scripts/generate-dev-jwt-keys.ps1
        if ($LASTEXITCODE -ne 0) { throw "Development JWT key generation failed." }
    }
    & docker compose --project-name $ProjectName up --detach --wait --build
    if ($LASTEXITCODE -ne 0) { throw "Isolated Compose startup failed." }

    Push-Location "frontend"
    try {
        $env:PLAYWRIGHT_BASE_URL = "http://127.0.0.1:$($env:CLINICFLOW_FRONTEND_PORT)"
        $env:PLAYWRIGHT_SKIP_WEBSERVER = "true"
        & npm run test:e2e
        if ($LASTEXITCODE -ne 0) { throw "Compose-backed Playwright validation failed." }
    }
    finally { Pop-Location }
}
finally {
    try {
        & docker compose --project-name $ProjectName down --volumes --remove-orphans
        if ($LASTEXITCODE -ne 0) { Write-Warning "Isolated Compose cleanup failed with exit code $LASTEXITCODE." }
    }
    catch { Write-Warning "Isolated Compose cleanup failed: $($_.Exception.Message)" }
    Pop-Location
}
