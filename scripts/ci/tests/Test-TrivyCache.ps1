[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../Security-Common.ps1')
$Directory = Join-Path $SecurityReportRoot ('cache-tests-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $Directory | Out-Null
function Write-Metadata([int]$Version,[DateTimeOffset]$Updated) {
    @{Version=$Version;UpdatedAt=$Updated.ToString('O')} | ConvertTo-Json | Set-Content (Join-Path $Directory 'metadata.json')
}
function Expect-Failure([scriptblock]$Action) {
    $Failed = $false
    try { & $Action } catch { $Failed = $true }
    if (-not $Failed) { throw 'Expected invalid cache metadata to fail.' }
}
Expect-Failure { Test-TrivyDatabase $Directory 2 }
[IO.File]::WriteAllBytes((Join-Path $Directory 'trivy.db'),[byte[]]@(1))
Write-Metadata 2 ([DateTimeOffset]::UtcNow.AddHours(-1))
Test-TrivyDatabase $Directory 2
Write-Metadata 2 ([DateTimeOffset]::UtcNow.AddHours(-25))
Expect-Failure { Test-TrivyDatabase $Directory 2 }
Write-Metadata 3 ([DateTimeOffset]::UtcNow.AddHours(-1))
Expect-Failure { Test-TrivyDatabase $Directory 2 }
Write-Metadata 2 ([DateTimeOffset]::UtcNow.AddMinutes(11))
Expect-Failure { Test-TrivyDatabase $Directory 2 }
# The Java DB is accepted between Trivy's three-day refreshes but not past the Java limit.
[IO.File]::WriteAllBytes((Join-Path $Directory 'trivy-java.db'),[byte[]]@(1))
Write-Metadata 1 ([DateTimeOffset]::UtcNow.AddHours(-71))
Test-TrivyDatabase $Directory 1
Write-Metadata 1 ([DateTimeOffset]::UtcNow.AddHours(-97))
Expect-Failure { Test-TrivyDatabase $Directory 1 }
Write-Metadata 2 ([DateTimeOffset]::UtcNow.AddHours(-1))
Expect-Failure { Test-TrivyDatabase $Directory 1 }
Write-Host 'Trivy cache missing, fresh, stale, incompatible, future-date and Java DB age tests passed.'
