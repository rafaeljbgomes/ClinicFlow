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

function Wait-HttpEndpoint {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Uri,
        [int]$TimeoutSeconds = 240
    )

    $Deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            $Response = Invoke-WebRequest -Uri $Uri -Method Get -TimeoutSec 5 -SkipHttpErrorCheck
            if ([int]$Response.StatusCode -lt 500) { return }
        }
        catch {
            # The diagnostic block below captures container logs if readiness expires.
        }
        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::UtcNow -lt $Deadline)

    throw "Frontend did not become ready at $Uri within $TimeoutSeconds seconds."
}

Push-Location $RepositoryRoot
try {
    & docker compose --file docker-compose.yml --file docker-compose.ci.yml --project-name $ProjectName up --detach --build
    if ($LASTEXITCODE -ne 0) { throw "Isolated Compose startup failed." }

    $FrontendUri = "http://${RuntimeHost}:$($env:CLINICFLOW_FRONTEND_PORT)"
    Wait-HttpEndpoint -Uri $FrontendUri

    Push-Location "frontend"
    try {
        $env:PLAYWRIGHT_BASE_URL = $FrontendUri
        $env:PLAYWRIGHT_SKIP_WEBSERVER = "true"
        & npm ci
        if ($LASTEXITCODE -ne 0) { throw "Frontend dependency installation failed." }
        & npm run test:e2e
        if ($LASTEXITCODE -ne 0) { throw "Compose-backed Playwright validation failed." }
    }
    finally { Pop-Location }
}
catch {
    $StartupFailure = $_
    Write-Warning "Isolated Compose validation failed. Capturing service state and logs before cleanup."
    try {
        & docker compose --file docker-compose.yml --file docker-compose.ci.yml --project-name $ProjectName ps --all
        & docker compose --file docker-compose.yml --file docker-compose.ci.yml --project-name $ProjectName logs --no-color
    }
    catch {
        Write-Warning "Unable to capture isolated Compose diagnostics: $($_.Exception.Message)"
    }
    throw $StartupFailure
}
finally {
    try {
        & docker compose --file docker-compose.yml --file docker-compose.ci.yml --project-name $ProjectName down --volumes --remove-orphans
        if ($LASTEXITCODE -ne 0) { Write-Warning "Isolated Compose cleanup failed with exit code $LASTEXITCODE." }
    }
    catch { Write-Warning "Isolated Compose cleanup failed: $($_.Exception.Message)" }
    Pop-Location
}
