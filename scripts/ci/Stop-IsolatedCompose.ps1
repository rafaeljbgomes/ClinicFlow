[CmdletBinding()]
param([Parameter(Mandatory)][string]$RunId)

$ErrorActionPreference = "Continue"
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot "Compose-Project.ps1")
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$ProjectName = ConvertTo-ComposeProjectName -RunId $RunId
Set-IsolatedPorts -RunId $RunId

Push-Location $RepositoryRoot
try {
    & docker compose --project-name $ProjectName down --volumes --remove-orphans
    if ($LASTEXITCODE -ne 0) { Write-Warning "Cleanup for $ProjectName returned exit code $LASTEXITCODE." }
}
finally { Pop-Location }
