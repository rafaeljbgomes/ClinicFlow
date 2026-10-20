[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$VerifiedCommit,
    # 15368 is the GitHub Actions app; binding checks to it stops other apps from satisfying them.
    [long]$IntegrationId = 15368,
    # Require PR branches to be up to date with main before merging.
    [switch]$Strict,
    [switch]$Apply
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$Settings = Get-Content -Raw "$Root/.ci/security.json" | ConvertFrom-Json
function Read-GitHub([string]$Endpoint) {
    $Result = & gh api $Endpoint
    if ($LASTEXITCODE -ne 0) { throw "Cannot read GitHub endpoint $Endpoint" }
    return ($Result | ConvertFrom-Json)
}
$Repo = 'repos/rafaeljbgomes/ClinicFlow'
$Rulesets = Read-GitHub "$Repo/rulesets"
$Selected = @($Rulesets | Where-Object name -eq 'main_protection')
if ($Selected.Count -ne 1) { throw 'Expected exactly one existing main_protection ruleset.' }
$Current = Read-GitHub "$Repo/rulesets/$($Selected[0].id)"
$Runs = (Read-GitHub "$Repo/commits/$VerifiedCommit/check-runs?per_page=100").check_runs
foreach ($Name in $Settings.requiredChecks) {
    $Latest = @($Runs | Where-Object { $_.name -eq $Name -and $_.app.id -eq $IntegrationId } | Sort-Object started_at -Descending)
    if (-not $Latest -or $Latest[0].conclusion -ne 'success') { throw "Missing successful check from the expected app ($IntegrationId): $Name" }
}
$Required = @{ type = 'required_status_checks'; parameters = @{
    strict_required_status_checks_policy = $Strict.IsPresent
    required_status_checks = @($Settings.requiredChecks | ForEach-Object { @{context = $_; integration_id = $IntegrationId} })
} }
$Proposal = @{
    name = $Current.name; target = $Current.target; enforcement = $Current.enforcement
    conditions = $Current.conditions; bypass_actors = $Current.bypass_actors
    rules = @($Current.rules | Where-Object type -ne 'required_status_checks') + @($Required)
}
$Directory = "$Root/reports/security"
New-Item -ItemType Directory -Force $Directory | Out-Null
$Path = "$Directory/ruleset-proposal.json"
$Proposal | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $Path
if ($Apply) {
    & gh api --method PUT "$Repo/rulesets/$($Current.id)" --input $Path
    if ($LASTEXITCODE -ne 0) { throw 'GitHub rejected the ruleset update.' }
} else { Write-Host "Review $Path. No GitHub settings have been changed." }
