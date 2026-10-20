[CmdletBinding()]
param([Parameter(Mandatory)][string]$RunId)
$ErrorActionPreference = 'Continue'
. (Join-Path $PSScriptRoot 'Compose-Project.ps1')
$Root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$Project = ConvertTo-ComposeProjectName -RunId "$RunId-security"
Set-IsolatedPorts -RunId "$RunId-security"
$env:CLINICFLOW_SECURITY_TAG = $Project
Push-Location $Root
try {
    & docker compose --file docker-compose.yml --file docker-compose.ci.yml --file docker-compose.security.yml --project-name $Project --profile security down --volumes --remove-orphans --rmi local
    if ($LASTEXITCODE -ne 0) { Write-Warning "Security cleanup failed for $Project; investigate its labeled resources." }
    # Only tags created for this isolated run, never vendor/base images.
    & docker image rm "clinicflow/zap:$Project" "clinicflow/gateway:ci-$Project" "clinicflow/postgres-bootstrap:ci-$Project" 2>$null
}
finally { Pop-Location }
