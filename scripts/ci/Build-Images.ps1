[CmdletBinding()]
param([string]$RunId)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$ComposeArguments = @("compose", "--file", "docker-compose.yml", "--file", "docker-compose.ci.yml")

if ($RunId) {
    . (Join-Path $PSScriptRoot "Compose-Project.ps1")
    $ProjectName = ConvertTo-ComposeProjectName -RunId $RunId
    $ComposeArguments += @("--project-name", $ProjectName)
}

Push-Location $RepositoryRoot
try {
    & docker @ComposeArguments build auth-service patient-service appointment-service clinical-service notification-service frontend
    if ($LASTEXITCODE -ne 0) { throw "Container image build failed." }
}
finally { Pop-Location }
