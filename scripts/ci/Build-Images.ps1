[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path

Push-Location $RepositoryRoot
try {
    & docker compose build auth-service patient-service appointment-service clinical-service notification-service frontend
    if ($LASTEXITCODE -ne 0) { throw "Container image build failed." }
}
finally { Pop-Location }
