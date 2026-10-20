[CmdletBinding()]
param([Parameter(Mandatory)][string]$Component)

# Converts the component's Trivy JSON reports to SARIF for GitHub code scanning. Each file gets its own
# automation category so one upload can carry several Trivy runs without colliding.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$Source = Join-Path $RepositoryRoot "reports/security/$Component"
$Destination = Join-Path $RepositoryRoot 'reports/sarif'
New-Item -ItemType Directory -Force $Destination | Out-Null

$Reports = @(Get-ChildItem -LiteralPath $Source -Filter '*.json' -File -ErrorAction SilentlyContinue | Where-Object {
    # Skip gate dispositions, CycloneDX inventories and database/Sonar metadata.
    $_.Name -notmatch '(\.gate|bom|metadata|sonar-gate)\.json$'
})
foreach ($Report in $Reports) {
    $Target = [IO.Path]::GetFileNameWithoutExtension($Report.Name)
    $Output = Join-Path $Destination "$Component-$Target.sarif"
    & trivy convert --format sarif --output $Output $Report.FullName
    if ($LASTEXITCODE -ne 0) { throw "Unable to convert $($Report.FullName) to SARIF." }
    $Sarif = Get-Content -Raw -LiteralPath $Output | ConvertFrom-Json -Depth 100
    foreach ($Run in @($Sarif.runs)) {
        $Run | Add-Member -NotePropertyName automationDetails -NotePropertyValue @{ id = "trivy/$Component/$Target/" } -Force
    }
    $Sarif | ConvertTo-Json -Depth 100 -Compress | Set-Content -LiteralPath $Output
    Write-Host "Exported $Output"
}
if (-not $Reports) { Write-Host "No Trivy reports found for $Component." }
