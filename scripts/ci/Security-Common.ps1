[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$SecurityRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$SecuritySettings = Get-Content -Raw (Join-Path $SecurityRoot '.ci/security.json') | ConvertFrom-Json
$SecurityReportRoot = Join-Path $SecurityRoot 'reports/security'
# Hosted runners are discarded after each job; the workflow caches this directory between runs.
if (-not $env:TRIVY_CACHE_DIR -and $env:GITHUB_ACTIONS -eq 'true') { $env:TRIVY_CACHE_DIR = Join-Path $env:RUNNER_TEMP 'trivy-cache' }
New-Item -ItemType Directory -Force $SecurityReportRoot | Out-Null
function Invoke-SecurityTool {
    param([string]$Tool, [string[]]$Arguments)
    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Tool failed with exit code $LASTEXITCODE" }
}
function Register-SecurityViolation {
    param([string]$Message)
    Add-Content -LiteralPath (Join-Path $SecurityReportRoot 'violations.txt') -Value $Message
    if ($env:CLINICFLOW_SECURITY_MODE -eq 'enforce') { throw $Message }
    if ($env:CLINICFLOW_SECURITY_MODE -ne 'report') { throw 'Set CLINICFLOW_SECURITY_MODE to report or enforce in the CI configuration.' }
    if ($env:GITHUB_ACTIONS -eq 'true') { Write-Host "::warning title=Security report mode::$Message" }
    else { Write-Warning "$Message (report mode: Jenkins must mark this build UNSTABLE)" }
}
function Test-TrivyDatabase {
    param([string]$Directory, [int]$Version)
    $DatabasePath = Join-Path $Directory $(if ($Version -eq 1) { 'trivy-java.db' } else { 'trivy.db' })
    if (-not (Test-Path $DatabasePath) -or (Get-Item $DatabasePath).Length -eq 0) { throw 'Missing/empty Trivy database' }
    $MetadataPath = Join-Path $Directory 'metadata.json'
    if (-not (Test-Path $MetadataPath)) { throw "Missing Trivy database metadata: $Directory" }
    $Metadata = Get-Content -Raw $MetadataPath | ConvertFrom-Json
    $Updated = if ($Metadata.UpdatedAt -is [datetime]) { [DateTimeOffset]$Metadata.UpdatedAt } else { [DateTimeOffset]::Parse($Metadata.UpdatedAt,[Globalization.CultureInfo]::InvariantCulture) }
    $Age = [DateTimeOffset]::UtcNow - $Updated
    if ($Metadata.Version -ne $Version -or $Age.TotalHours -gt $SecuritySettings.trivy.databaseMaxAgeHours -or $Age.TotalMinutes -lt -10) {
        throw "Trivy database is incompatible, stale, or future-dated: $Directory"
    }
}
function Initialize-TrivyDatabase {
    if (-not $env:TRIVY_CACHE_DIR) { throw 'TRIVY_CACHE_DIR must point outside the cleaned workspace.' }
    $ActualVersion = & trivy --version
    $VersionPattern = '(?m)^Version:\s+' + [regex]::Escape($SecuritySettings.trivy.version) + '\s*$'
    if ($LASTEXITCODE -ne 0 -or ($ActualVersion -join [Environment]::NewLine) -notmatch $VersionPattern) { throw 'Unexpected Trivy version' }
    foreach ($Kind in @(@('db', '--download-db-only', 2), @('java-db', '--download-java-db-only', 1))) {
        & trivy image --cache-dir $env:TRIVY_CACHE_DIR $Kind[1] --no-progress
        if ($LASTEXITCODE -ne 0) { Write-Warning "Database refresh failed; checking bounded cached database: $($Kind[0])" }
        Test-TrivyDatabase -Directory (Join-Path $env:TRIVY_CACHE_DIR $Kind[0]) -Version $Kind[2]
    }
}
function Invoke-TrivyGate {
    param([string]$Component, [string]$Target, [string]$Kind, [string[]]$ScanArguments)
    $Directory = Join-Path $SecurityReportRoot $Component
    New-Item -ItemType Directory -Force $Directory | Out-Null
    $Report = Join-Path $Directory "$Target.json"
    $IgnoreFile = Join-Path $SecurityReportRoot 'empty-ignore'
    [IO.File]::WriteAllText($IgnoreFile, '')
    $Arguments = @($ScanArguments[0], '--cache-dir', $env:TRIVY_CACHE_DIR, '--format', 'json', '--output', $Report, '--exit-code', '0', '--ignorefile', $IgnoreFile)
    if ($Kind -eq 'vulnerability') { $Arguments += @('--skip-db-update', '--skip-java-db-update', '--no-progress') }
    $Arguments += $ScanArguments | Select-Object -Skip 1
    Invoke-SecurityTool trivy $Arguments
    & node (Join-Path $PSScriptRoot 'security-policy.cjs') evaluate $Component $Target $Kind $Report
    if ($LASTEXITCODE -eq 2) { Register-SecurityViolation "$Component/$Target contains new High/Critical findings." }
    elseif ($LASTEXITCODE -ne 0) { throw "$Component/$Target security report could not be validated." }
}
