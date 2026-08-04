[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path

Push-Location $RepositoryRoot
try {
    & ./mvnw -B -ntp clean verify
    if ($LASTEXITCODE -ne 0) { throw "Backend full verification failed." }

    & ./mvnw -B -ntp -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage
    if ($LASTEXITCODE -ne 0) { throw "Mutation verification failed." }
}
finally { Pop-Location }
