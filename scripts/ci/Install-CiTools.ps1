[CmdletBinding()]
param(
    [Parameter(Mandatory)][string[]]$Tools,
    [string]$Destination = $(if ($env:RUNNER_TOOL_CACHE) { Join-Path $env:RUNNER_TOOL_CACHE 'clinicflow' } else { Join-Path ([IO.Path]::GetTempPath()) 'clinicflow-tools' })
)

# Installs the checksum-pinned Linux CI tools declared in .ci/tools.json.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $IsLinux) { throw 'Install-CiTools.ps1 installs Linux x64 binaries only.' }
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
# Accept "a,b" or "a b" as well as an array, since pwsh -File passes a single string.
$Tools = @($Tools | ForEach-Object { $_ -split '[,\s]+' } | Where-Object { $_ })
$Manifest = Get-Content -Raw (Join-Path $RepositoryRoot '.ci/tools.json') | ConvertFrom-Json

foreach ($Name in $Tools) {
    $Tool = $Manifest.tools.$Name
    if (-not $Tool) { throw "Unknown CI tool: $Name" }
    $Version = $Tool.version
    $InstallDirectory = Join-Path $Destination "$Name/$Version"
    $Binary = Join-Path $InstallDirectory ($Tool.binary.Replace('{version}', $Version))
    $Marker = Join-Path $InstallDirectory '.sha256'

    if (-not ((Test-Path -LiteralPath $Marker) -and (Get-Content -Raw $Marker).Trim() -eq $Tool.sha256 -and (Test-Path -LiteralPath $Binary))) {
        if (Test-Path -LiteralPath $InstallDirectory) { Remove-Item -LiteralPath $InstallDirectory -Recurse -Force }
        New-Item -ItemType Directory -Force $InstallDirectory | Out-Null
        $Url = $Tool.url.Replace('{version}', $Version)
        $Archive = Join-Path ([IO.Path]::GetTempPath()) ("$Name-$Version-" + [IO.Path]::GetFileName(([Uri]$Url).AbsolutePath))
        Invoke-WebRequest -Uri $Url -OutFile $Archive -MaximumRetryCount 3 -RetryIntervalSec 5
        $Actual = (Get-FileHash -LiteralPath $Archive -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($Actual -ne $Tool.sha256) { throw "$Name $Version checksum mismatch: expected $($Tool.sha256), got $Actual." }

        if ($Archive -match '\.tar\.gz$') { & tar -xzf $Archive -C $InstallDirectory }
        else { & unzip -q -o $Archive -d $InstallDirectory }
        if ($LASTEXITCODE -ne 0) { throw "Unable to extract $Name $Version." }
        Remove-Item -LiteralPath $Archive -Force
        if (-not (Test-Path -LiteralPath $Binary)) { throw "$Name $Version archive did not contain $($Tool.binary)." }
        & chmod +x $Binary
        $Tool.sha256 | Set-Content -LiteralPath $Marker
    }

    $BinaryDirectory = Split-Path -Parent $Binary
    $env:PATH = $BinaryDirectory + [IO.Path]::PathSeparator + $env:PATH
    if ($env:GITHUB_PATH) { Add-Content -LiteralPath $env:GITHUB_PATH -Value $BinaryDirectory }
    Write-Host "Installed $Name $Version"
}
