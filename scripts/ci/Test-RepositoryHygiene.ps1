[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path

Push-Location $RepositoryRoot
try {
    & git diff --check HEAD
    if ($LASTEXITCODE -ne 0) { throw "Git whitespace validation failed." }

    $Forbidden = & git ls-files | Where-Object {
        $_ -match '(^|/)(\.env($|\.)|node_modules/|target/|\.next/|playwright-report/|test-results/)' -or
        $_ -match '\.(pem|key|crt|p12|pfx|jks)$'
    }
    if ($Forbidden) { throw "Tracked generated or sensitive paths were found: $($Forbidden -join ', ')" }

    & gitleaks git --redact --no-banner .
    if ($LASTEXITCODE -ne 0) { throw "Gitleaks detected a potential secret." }
}
finally { Pop-Location }
