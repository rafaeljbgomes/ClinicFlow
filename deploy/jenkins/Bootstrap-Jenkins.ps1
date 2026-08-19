[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$SecretsDirectory = Join-Path $PSScriptRoot "secrets"
$AdminPasswordPath = Join-Path $SecretsDirectory "admin-password"
$AgentKeyPath = Join-Path $SecretsDirectory "controller_agent_ed25519"
$AgentHostKeyPath = Join-Path $SecretsDirectory "agent_host_ed25519"
$AgentHostPublicKeySecretPath = Join-Path $SecretsDirectory "agent-host-public-key"

function New-SshKeyPair([string] $Path, [string] $Comment) {
    $Keygen = [System.Diagnostics.ProcessStartInfo]::new("ssh-keygen")
    $Keygen.UseShellExecute = $false
    foreach ($Argument in @("-q", "-t", "ed25519", "-N", "", "-C", $Comment, "-f", $Path)) {
        $Keygen.ArgumentList.Add($Argument)
    }
    $KeygenProcess = [System.Diagnostics.Process]::Start($Keygen)
    $KeygenProcess.WaitForExit()
    if ($KeygenProcess.ExitCode -ne 0) { throw "ssh-keygen failed with exit code $($KeygenProcess.ExitCode)" }
}

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
    New-SshKeyPair -Path $AgentKeyPath -Comment "clinicflow-local-jenkins-agent"
}

if (-not (Test-Path -LiteralPath $AgentHostKeyPath)) {
    New-SshKeyPair -Path $AgentHostKeyPath -Comment "clinicflow-local-agent-host"
}

$AgentHostPublicKey = [System.IO.File]::ReadAllText("$AgentHostKeyPath.pub").Trim()
[System.IO.File]::WriteAllText(
    $AgentHostPublicKeySecretPath,
    $AgentHostPublicKey + [Environment]::NewLine,
    [System.Text.UTF8Encoding]::new($false))

if (-not ($IsWindows -or $env:OS -eq "Windows_NT")) {
    & chmod 600 $AgentKeyPath
    if ($LASTEXITCODE -ne 0) { throw "Could not restrict the local agent key permissions." }
    & chmod 600 $AgentHostKeyPath
    if ($LASTEXITCODE -ne 0) { throw "Could not restrict the local agent host key permissions." }
}

Write-Host "Jenkins local secrets are ready in the ignored secrets directory."
Write-Host "Start the stack with: docker compose --file deploy/jenkins/compose.yml up --build --detach"
