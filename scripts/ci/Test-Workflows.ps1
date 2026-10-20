[CmdletBinding()]
param()

# Lints the GitHub Actions workflows with actionlint and zizmor. Uses local binaries when they are on
# PATH, otherwise the pinned container images, so the same check runs on a workstation before pushing.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$Tools = (Get-Content -Raw (Join-Path $RepositoryRoot '.ci/tools.json') | ConvertFrom-Json).tools

function Invoke-Linter {
    param([string]$Name, [string]$Image, [string[]]$Arguments)
    if (Get-Command $Name -ErrorAction SilentlyContinue) {
        & $Name @Arguments
    } elseif (Get-Command docker -ErrorAction SilentlyContinue) {
        & docker run --rm --volume "${RepositoryRoot}:/repo" --workdir /repo $Image @Arguments
    } else {
        throw "$Name is not installed and Docker is unavailable."
    }
    if ($LASTEXITCODE -ne 0) { throw "$Name reported workflow problems." }
}

Push-Location $RepositoryRoot
try {
    Invoke-Linter actionlint "rhysd/actionlint:$($Tools.actionlint.version)" @('-color')
    # Offline mode keeps results deterministic and avoids GitHub API rate limits.
    Invoke-Linter zizmor "ghcr.io/zizmorcore/zizmor:$($Tools.zizmor.version)" @('--offline', '--min-severity', 'medium', '.github')
}
finally { Pop-Location }
Write-Host 'actionlint and zizmor found no workflow problems.'
