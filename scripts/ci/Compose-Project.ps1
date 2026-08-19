function ConvertTo-ComposeProjectName {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$RunId)

    $Normalized = $RunId.ToLowerInvariant() -replace '[^a-z0-9_-]', '-'
    $Normalized = $Normalized.Trim('-_')
    if (-not $Normalized) { throw "RunId must contain at least one alphanumeric character." }
    return "clinicflow-ci-$Normalized"
}

function Set-IsolatedPorts {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$RunId)

    $Hash = [System.Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($RunId))
    $Offset = ([BitConverter]::ToUInt16($Hash, 0) % 1000) * 10
    $Base = 32000 + $Offset
    $env:CLINICFLOW_POSTGRES_PORT = [string]($Base + 1)
    $env:CLINICFLOW_RABBITMQ_PORT = [string]($Base + 2)
    $env:CLINICFLOW_RABBITMQ_MANAGEMENT_PORT = [string]($Base + 3)
    $env:CLINICFLOW_AUTH_PORT = [string]($Base + 4)
    $env:CLINICFLOW_PATIENT_PORT = [string]($Base + 5)
    $env:CLINICFLOW_APPOINTMENT_PORT = [string]($Base + 6)
    $env:CLINICFLOW_NOTIFICATION_PORT = [string]($Base + 7)
    $env:CLINICFLOW_CLINICAL_PORT = [string]($Base + 8)
    $env:CLINICFLOW_FRONTEND_PORT = [string]($Base + 9)
}
