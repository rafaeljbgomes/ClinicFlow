[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet(
        "auth-service",
        "patient-service",
        "appointment-service",
        "clinical-service",
        "notification-service",
        "frontend"
    )]
    [string]$Component,

    [Parameter(Mandatory)]
    [ValidatePattern('^[a-z0-9][a-z0-9._/-]*:[A-Za-z0-9_][A-Za-z0-9_.-]*$')]
    [string]$Tag
)

$ErrorActionPreference = "Continue"
Set-StrictMode -Version Latest

& docker image inspect $Tag *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "No temporary $Component image exists for tag $Tag."
    exit 0
}

& docker image rm --force $Tag
if ($LASTEXITCODE -ne 0) { Write-Warning "Unable to remove temporary $Component image $Tag." }
