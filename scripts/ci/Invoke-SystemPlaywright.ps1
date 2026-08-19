[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$RunId,
    [string]$RuntimeHost = "127.0.0.1"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "Compose-Project.ps1")
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$ProjectName = ConvertTo-ComposeProjectName -RunId $RunId
Set-IsolatedPorts -RunId $RunId
$FrontendUri = "http://${RuntimeHost}:$($env:CLINICFLOW_FRONTEND_PORT)"

Push-Location (Join-Path $RepositoryRoot "frontend")
try {
    $env:PLAYWRIGHT_BASE_URL = $FrontendUri
    $env:PLAYWRIGHT_SKIP_WEBSERVER = "true"
    & npm run test:e2e
    if ($LASTEXITCODE -ne 0) { throw "Compose-backed Playwright validation failed." }
}
catch {
    $BrowserFailure = $_
    Pop-Location
    Push-Location $RepositoryRoot
    try {
        & docker compose --file docker-compose.yml --file docker-compose.ci.yml --project-name $ProjectName ps --all
        & docker compose --file docker-compose.yml --file docker-compose.ci.yml --project-name $ProjectName logs --no-color
    }
    finally { Pop-Location }
    throw $BrowserFailure
}
finally {
    if ((Get-Location).Path -ne $RepositoryRoot) { Pop-Location }
}
