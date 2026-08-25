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

function Test-TcpPortOccupied {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$HostName,
        [Parameter(Mandatory)][int]$Port
    )

    $Client = [Net.Sockets.TcpClient]::new()
    try {
        $Connection = $Client.ConnectAsync($HostName, $Port)
        return $Connection.Wait(500) -and $Client.Connected
    }
    catch { return $false }
    finally { $Client.Dispose() }
}

function Wait-DockerHostPorts {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$HostName,
        [Parameter(Mandatory)][int[]]$Ports,
        [int]$TimeoutSeconds = 120
    )

    $FirstPort = ($Ports | Measure-Object -Minimum).Minimum
    $LastPort = ($Ports | Measure-Object -Maximum).Maximum
    $Deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    $ClearChecks = 0
    do {
        $Containers = @(& docker ps --quiet --filter "publish=$FirstPort-$LastPort/tcp")
        if ($LASTEXITCODE -ne 0) { throw "Unable to inspect Docker host-port ownership." }
        $OccupiedPorts = @($Ports | Where-Object { Test-TcpPortOccupied -HostName $HostName -Port $_ })
        if ($Containers.Count -eq 0 -and $OccupiedPorts.Count -eq 0) {
            $ClearChecks++
            if ($ClearChecks -ge 2) { return }
        }
        else { $ClearChecks = 0 }
        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::UtcNow -lt $Deadline)

    throw "Docker host ports $FirstPort-$LastPort remained occupied for $TimeoutSeconds seconds."
}

Push-Location $RepositoryRoot
try {
    $HostPorts = @(
        $env:CLINICFLOW_POSTGRES_PORT,
        $env:CLINICFLOW_RABBITMQ_PORT,
        $env:CLINICFLOW_RABBITMQ_MANAGEMENT_PORT,
        $env:CLINICFLOW_AUTH_PORT,
        $env:CLINICFLOW_PATIENT_PORT,
        $env:CLINICFLOW_APPOINTMENT_PORT,
        $env:CLINICFLOW_NOTIFICATION_PORT,
        $env:CLINICFLOW_CLINICAL_PORT,
        $env:CLINICFLOW_FRONTEND_PORT
    ) | ForEach-Object { [int]$_ }
    Wait-DockerHostPorts -HostName $RuntimeHost -Ports $HostPorts
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
