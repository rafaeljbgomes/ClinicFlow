[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path

Push-Location $RepositoryRoot
try {
    & ./mvnw -B -ntp test
    if ($LASTEXITCODE -ne 0) { throw "Backend fast verification failed." }
}
finally { Pop-Location }
