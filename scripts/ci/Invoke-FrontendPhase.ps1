[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet("Install", "Unit", "Quality", "Build", "Package")]
    [string]$Phase
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$FrontendDirectory = Join-Path $RepositoryRoot "frontend"

Push-Location $FrontendDirectory
try {
    switch ($Phase) {
        "Install" {
            & npm ci
            if ($LASTEXITCODE -ne 0) { throw "Frontend dependency installation failed." }
        }
        "Unit" {
            & npm run test:unit
            if ($LASTEXITCODE -ne 0) { throw "Frontend unit tests failed." }
        }
        "Quality" {
            & npm run lint
            if ($LASTEXITCODE -ne 0) { throw "Frontend lint failed." }
            & npm run typecheck -- --incremental false
            if ($LASTEXITCODE -ne 0) { throw "Frontend typecheck failed." }
        }
        "Build" {
            & npm run build
            if ($LASTEXITCODE -ne 0) { throw "Frontend production build failed." }
        }
        "Package" {
            $RequiredPaths = @(".next/standalone", ".next/static", "public")
            foreach ($Path in $RequiredPaths) {
                if (-not (Test-Path -LiteralPath $Path)) {
                    throw "Frontend package input is missing: $Path"
                }
            }

            $ReportsDirectory = Join-Path $RepositoryRoot "reports/frontend"
            $StagingDirectory = Join-Path $ReportsDirectory "package"
            $ArchivePath = Join-Path $ReportsDirectory "frontend-standalone.zip"
            if (Test-Path -LiteralPath $StagingDirectory) {
                Remove-Item -LiteralPath $StagingDirectory -Recurse -Force
            }
            New-Item -ItemType Directory -Path $StagingDirectory -Force | Out-Null
            Copy-Item -LiteralPath ".next/standalone" -Destination (Join-Path $StagingDirectory "standalone") -Recurse
            New-Item -ItemType Directory -Path (Join-Path $StagingDirectory "static") -Force | Out-Null
            Copy-Item -Path ".next/static/*" -Destination (Join-Path $StagingDirectory "static") -Recurse
            Copy-Item -LiteralPath "public" -Destination (Join-Path $StagingDirectory "public") -Recurse
            Compress-Archive -Path (Join-Path $StagingDirectory "*") -DestinationPath $ArchivePath -Force
            Write-Host "Created frontend package: $ArchivePath"
        }
    }
}
finally { Pop-Location }
