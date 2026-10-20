[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$Errors = @()
Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1' | ForEach-Object {
    $Tokens = $null; $ParseErrors = $null
    [System.Management.Automation.Language.Parser]::ParseFile($_.FullName,[ref]$Tokens,[ref]$ParseErrors) | Out-Null
    $Errors += $ParseErrors
}
if ($Errors.Count) { $Errors | Write-Error; throw 'CI PowerShell parsing failed.' }
Get-ChildItem -LiteralPath $Root -Filter pom.xml -Recurse | Where-Object FullName -notmatch 'node_modules' | ForEach-Object { [xml](Get-Content -Raw $_.FullName) | Out-Null }
$SavedTag = $env:CLINICFLOW_SECURITY_TAG
$env:CLINICFLOW_SECURITY_TAG = 'config-validation'
Push-Location $Root
try {
    & docker compose --file docker-compose.yml --file docker-compose.ci.yml --file docker-compose.security.yml --profile security config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Security Compose configuration is invalid.' }
    & docker compose --file deploy/jenkins/compose.yml config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Jenkins Compose configuration is invalid.' }
    & node --check scripts/ci/security-policy.cjs
    if ($LASTEXITCODE -ne 0) { throw 'Policy JavaScript parsing failed.' }
}
finally { $env:CLINICFLOW_SECURITY_TAG = $SavedTag; Pop-Location }
Write-Host 'PowerShell, Maven XML, Compose, and policy syntax checks passed.'
