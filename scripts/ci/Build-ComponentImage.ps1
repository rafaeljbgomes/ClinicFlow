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

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$Dockerfile = Join-Path $RepositoryRoot "$Component/Dockerfile"
$BuildContext = if ($Component -eq "frontend") {
    Join-Path $RepositoryRoot "frontend"
} else {
    $RepositoryRoot
}

& docker build --tag $Tag --file $Dockerfile $BuildContext
if ($LASTEXITCODE -ne 0) { throw "$Component image build failed." }
