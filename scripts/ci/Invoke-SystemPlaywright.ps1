[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$RunId,
    # The browser must stay on the origin registered as the realm's redirect URI (CLINICFLOW_PUBLIC_URL in
    # docker-compose.ci.yml), or the sign-in state cookie and the callback land on different hosts.
    [string]$RuntimeHost = "localhost"
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
