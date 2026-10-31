[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('patient-service','appointment-service','clinical-service','notification-service','frontend')][string]$Component)

# GitHub Actions entry point for the SonarQube Cloud gate. Sonar stays inactive until the repository
# variable SONAR_ORGANIZATION exists; once active, a missing token fails closed except on fork PRs,
# which never receive secrets and are analyzed again after merge.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if (-not $env:SONAR_ORGANIZATION) {
    Write-Host '::warning title=SonarQube Cloud not activated::Set the SONAR_ORGANIZATION repository variable and the SONAR_TOKEN secret to enforce the quality gate.'
    return
}
if (-not $env:SONAR_TOKEN) {
    $IsForkPullRequest = $env:GITHUB_EVENT_NAME -eq 'pull_request' -and $env:HEAD_REPOSITORY -and $env:HEAD_REPOSITORY -ne $env:GITHUB_REPOSITORY
    if ($IsForkPullRequest) {
        Write-Host '::notice title=SonarQube Cloud skipped::Fork pull requests receive no secrets; the gate runs after merge.'
        return
    }
    throw 'SONAR_TOKEN is not available although SonarQube Cloud is activated.'
}
& (Join-Path $PSScriptRoot 'Invoke-SonarAnalysis.ps1') -Component $Component
