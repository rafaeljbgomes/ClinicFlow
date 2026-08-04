[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$SecretsDirectory = Join-Path $PSScriptRoot "secrets"
$AdminPasswordPath = Join-Path $SecretsDirectory "admin-password"
$AgentKeyPath = Join-Path $SecretsDirectory "agent_ed25519"

New-Item -ItemType Directory -Path $SecretsDirectory -Force | Out-Null

if (-not (Test-Path -LiteralPath $AdminPasswordPath)) {
    $Bytes = [byte[]]::new(32)
    $Generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $Generator.GetBytes($Bytes) }
    finally { $Generator.Dispose() }
    $Password = [Convert]::ToBase64String($Bytes).TrimEnd("=")
    [System.IO.File]::WriteAllText($AdminPasswordPath, $Password + [Environment]::NewLine)
}

if (-not (Test-Path -LiteralPath $AgentKeyPath)) {
    # Windows PowerShell drops an empty native argument; literal quotes preserve an empty passphrase.
    & ssh-keygen -q -t ed25519 -N '""' -C "clinicflow-local-jenkins-agent" -f $AgentKeyPath
    if ($LASTEXITCODE -ne 0) { throw "ssh-keygen failed with exit code $LASTEXITCODE" }
}

Write-Host "Jenkins local secrets are ready in the ignored secrets directory."
Write-Host "Start the stack with: docker compose --file deploy/jenkins/compose.yml up --build --detach"
