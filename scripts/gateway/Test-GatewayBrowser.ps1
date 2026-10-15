[CmdletBinding()]
param([string]$Namespace='clinicflow',[string]$UiUrl='https://clinicflow.localhost',[string]$RequestUrl='https://127.0.0.1')
$ErrorActionPreference='Stop'
$Root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$Revision=(& helm status clinicflow-gateway -n $Namespace -o json | ConvertFrom-Json).version
if($LASTEXITCODE -ne 0){throw 'The application gateway must be installed before browser acceptance.'}
$Names=@('PLAYWRIGHT_BASE_URL','PLAYWRIGHT_REQUEST_BASE_URL','PLAYWRIGHT_SKIP_WEBSERVER','PLAYWRIGHT_IGNORE_HTTPS_ERRORS')
$Before=@{}
foreach($Name in $Names){$Before[$Name]=[Environment]::GetEnvironmentVariable($Name,'Process')}
try {
    & helm upgrade clinicflow-gateway (Join-Path $Root 'deploy/helm/gateway') -n $Namespace --reuse-values --set rateLimits.auth.average=100 --set rateLimits.auth.period=1s --set rateLimits.auth.burst=200 --set rateLimits.public.average=1000 --set rateLimits.public.burst=2000 --rollback-on-failure --wait=watcher --server-side=false --timeout=5m
    if($LASTEXITCODE -ne 0){throw 'Temporary functional-test quota update failed.'}
    & (Join-Path $PSScriptRoot 'Wait-GatewayRoutes.ps1') -Namespace $Namespace
    Start-Sleep -Seconds 3
    $env:PLAYWRIGHT_BASE_URL=$UiUrl
    $env:PLAYWRIGHT_REQUEST_BASE_URL=$RequestUrl
    $env:PLAYWRIGHT_SKIP_WEBSERVER='true'
    # Only the test browser ignores the uninstalled development CA. Strict CA/SAN
    # checks are required separately in Test-GatewayTraffic / the live contract.
    $env:PLAYWRIGHT_IGNORE_HTTPS_ERRORS='true'
    Push-Location (Join-Path $Root 'frontend')
    try {
        & npm run test:e2e
        if($LASTEXITCODE -ne 0){throw 'Kubernetes browser acceptance failed.'}
    } finally {Pop-Location}
} finally {
    foreach($Name in $Names){[Environment]::SetEnvironmentVariable($Name,$Before[$Name],'Process')}
    & helm rollback clinicflow-gateway $Revision -n $Namespace --wait=watcher --server-side=false --timeout=5m
    if($LASTEXITCODE -ne 0){throw 'Functional-test quota restoration failed.'}
    & (Join-Path $PSScriptRoot 'Wait-GatewayRoutes.ps1') -Namespace $Namespace
}
