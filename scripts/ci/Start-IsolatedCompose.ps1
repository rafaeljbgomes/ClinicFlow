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

function Write-ComposeDiagnostics {
    & docker compose --file docker-compose.yml --file docker-compose.ci.yml --project-name $ProjectName ps --all
    & docker compose --file docker-compose.yml --file docker-compose.ci.yml --project-name $ProjectName logs --no-color
}

function Wait-HttpEndpoint {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Uri,
        [int]$TimeoutSeconds = 240
    )

    $Deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            $Response = Invoke-WebRequest -Uri $Uri -Method Get -TimeoutSec 5 -SkipHttpErrorCheck
            if ([int]$Response.StatusCode -eq 200) { return }
        }
        catch {
            # Diagnostics are captured below if readiness expires.
        }
        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::UtcNow -lt $Deadline)

    throw "$Name did not become ready at $Uri within $TimeoutSeconds seconds."
}

Push-Location $RepositoryRoot
try {
    & docker compose --file docker-compose.yml --file docker-compose.ci.yml --project-name $ProjectName up --detach --no-build
    if ($LASTEXITCODE -ne 0) { throw "Isolated Compose startup failed." }
    $Endpoints = [ordered]@{
        "auth-service" = "http://${RuntimeHost}:$($env:CLINICFLOW_AUTH_PORT)/actuator/health"
        "patient-service" = "http://${RuntimeHost}:$($env:CLINICFLOW_PATIENT_PORT)/actuator/health"
        "appointment-service" = "http://${RuntimeHost}:$($env:CLINICFLOW_APPOINTMENT_PORT)/actuator/health"
        "clinical-service" = "http://${RuntimeHost}:$($env:CLINICFLOW_CLINICAL_PORT)/actuator/health"
        "notification-service" = "http://${RuntimeHost}:$($env:CLINICFLOW_NOTIFICATION_PORT)/actuator/health"
        "frontend" = "http://${RuntimeHost}:$($env:CLINICFLOW_FRONTEND_PORT)/login"
    }
    foreach ($Endpoint in $Endpoints.GetEnumerator()) {
        Wait-HttpEndpoint -Name $Endpoint.Key -Uri $Endpoint.Value
    }
}
catch {
    $StartupFailure = $_
    Write-Warning "Isolated Compose startup failed. Capturing service state and logs."
    try { Write-ComposeDiagnostics } catch { Write-Warning "Unable to capture Compose diagnostics: $($_.Exception.Message)" }
    throw $StartupFailure
}
finally { Pop-Location }
