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
        $FileName = [System.IO.Path]::GetFileName($_)
        $IsEnvironmentFile = $FileName -eq ".env" -or
            ($FileName.StartsWith(".env.") -and $FileName -ne ".env.example")
        $IsEnvironmentFile -or
        $_ -match '(^|/)(node_modules/|target/|\.next/|playwright-report/|test-results/)' -or
        $_ -match '\.(pem|key|crt|p12|pfx|jks)$'
    }
    if ($Forbidden) { throw "Tracked generated or sensitive paths were found: $($Forbidden -join ', ')" }

    # Scan the history under test, not every fetched branch: another branch's leak must fail its own PR, not this one.
    $LogOptions = '--full-history HEAD'
    if ($env:CHANGE_ID) {
        $Base = & git merge-base HEAD refs/remotes/origin/main
        if ($LASTEXITCODE -ne 0 -or -not $Base) { throw "Unable to find the merge base with main." }
        $LogOptions = "$Base..HEAD"
    }
    & betterleaks git --redact --no-banner --log-opts $LogOptions .
    if ($LASTEXITCODE -ne 0) { throw "Betterleaks detected an unreviewed potential secret." }
}
finally { Pop-Location }
