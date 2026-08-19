[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$FrontendDirectory = (Resolve-Path (Join-Path $PSScriptRoot "../../frontend")).Path

Push-Location $FrontendDirectory
try {
    & npm ci
    if ($LASTEXITCODE -ne 0) { throw "npm ci failed." }
    & npm run test:unit --if-present
    if ($LASTEXITCODE -ne 0) { throw "Frontend unit tests failed." }
    & npm run lint
    if ($LASTEXITCODE -ne 0) { throw "Frontend lint failed." }
    & npm run typecheck -- --incremental false
    if ($LASTEXITCODE -ne 0) { throw "Frontend typecheck failed." }
    & npm run build
    if ($LASTEXITCODE -ne 0) { throw "Frontend production build failed." }
}
finally { Pop-Location }
