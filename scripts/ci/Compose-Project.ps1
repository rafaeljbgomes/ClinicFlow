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
    $env:CLINICFLOW_CI_IMAGE_TAG = (ConvertTo-ComposeProjectName -RunId $RunId)
    $env:CLINICFLOW_POSTGRES_PORT = [string]($Base + 1)
    $env:CLINICFLOW_RABBITMQ_PORT = [string]($Base + 2)
    $env:CLINICFLOW_RABBITMQ_MANAGEMENT_PORT = [string]($Base + 3)
    # Keycloak's public URL, and so the run's token issuer (ADR-027).
    $env:CLINICFLOW_KEYCLOAK_PORT = [string]($Base + 4)
    $env:CLINICFLOW_GATEWAY_HTTP_PORT = [string]($Base + 9)
    # Existing Playwright scripts use this name for the public UI endpoint.
    $env:CLINICFLOW_FRONTEND_PORT = [string]($Base + 9)
}
