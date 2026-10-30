[CmdletBinding()]
# Request contexts use the UI origin too, so the session cookies the browser sign-in sets for it are sent.
param([string]$Namespace='clinicflow',[string]$UiUrl='https://clinicflow.localhost',[string]$RequestUrl='https://clinicflow.localhost',[string]$KeycloakUrl='https://auth.clinicflow.localhost',[int]$KeycloakAdminPort=8180)
$ErrorActionPreference='Stop'
$Root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$Revision=(& helm status clinicflow-gateway -n $Namespace -o json | ConvertFrom-Json).version
if($LASTEXITCODE -ne 0){throw 'The application gateway must be installed before browser acceptance.'}
$Names=@('PLAYWRIGHT_BASE_URL','PLAYWRIGHT_REQUEST_BASE_URL','PLAYWRIGHT_SKIP_WEBSERVER','PLAYWRIGHT_IGNORE_HTTPS_ERRORS','PLAYWRIGHT_KEYCLOAK_URL','PLAYWRIGHT_KEYCLOAK_BACKCHANNEL_URL','NODE_OPTIONS')
$Before=@{}
foreach($Name in $Names){$Before[$Name]=[Environment]::GetEnvironmentVariable($Name,'Process')}
# The admin API and master realm have no gateway route (ADR-027): the test helpers provision users and
# exchange codes through a port-forward on the admin hostname's port, as an operator would.
$Probe=[Net.Sockets.TcpClient]::new()
try { if($Probe.ConnectAsync('127.0.0.1',$KeycloakAdminPort).Wait(300) -and $Probe.Connected){throw "Port $KeycloakAdminPort is in use; stop the Compose Keycloak or the process holding it."} } finally {$Probe.Dispose()}
$PortForward=Start-Process kubectl -ArgumentList @('port-forward','svc/keycloak',"$($KeycloakAdminPort):8080",'-n',$Namespace) -PassThru -NoNewWindow -RedirectStandardOutput ([IO.Path]::GetTempFileName())
try {
    $Sink=if ($IsWindows) { 'NUL' } else { '/dev/null' }
    $Curl=if (Get-Command curl.exe -ErrorAction SilentlyContinue) { 'curl.exe' } else { 'curl' }
    $Deadline=[DateTimeOffset]::UtcNow.AddSeconds(30)
    do {
        Start-Sleep -Seconds 1
        $Status=& $Curl --silent --output $Sink --write-out '%{http_code}' --max-time 3 "http://localhost:$KeycloakAdminPort/realms/clinicflow/.well-known/openid-configuration"
    } until($Status -eq '200' -or [DateTimeOffset]::UtcNow -gt $Deadline)
    if($Status -ne '200'){throw "The Keycloak port-forward did not become ready (status '$Status')."}
    & helm upgrade clinicflow-gateway (Join-Path $Root 'deploy/helm/gateway') -n $Namespace --reuse-values --set rateLimits.auth.average=100 --set rateLimits.auth.period=1s --set rateLimits.auth.burst=200 --set rateLimits.public.average=1000 --set rateLimits.public.burst=2000 --rollback-on-failure --wait=watcher --server-side=false --timeout=5m
    if($LASTEXITCODE -ne 0){throw 'Temporary functional-test quota update failed.'}
    & (Join-Path $PSScriptRoot 'Wait-GatewayRoutes.ps1') -Namespace $Namespace
    Start-Sleep -Seconds 3
    $env:PLAYWRIGHT_BASE_URL=$UiUrl
    $env:PLAYWRIGHT_REQUEST_BASE_URL=$RequestUrl
    $env:PLAYWRIGHT_SKIP_WEBSERVER='true'
    $env:PLAYWRIGHT_KEYCLOAK_URL=$KeycloakUrl
    $env:PLAYWRIGHT_KEYCLOAK_BACKCHANNEL_URL="http://localhost:$KeycloakAdminPort"
    # Page-bound request contexts call the UI host from Node, which cannot resolve *.localhost on Windows.
    $env:NODE_OPTIONS=(@($Before['NODE_OPTIONS'],"--require=$(Join-Path $PSScriptRoot 'localhost-resolver.cjs')") | Where-Object { $_ }) -join ' '
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
    Stop-Process -Id $PortForward.Id -ErrorAction SilentlyContinue
    & helm rollback clinicflow-gateway $Revision -n $Namespace --wait=watcher --server-side=false --timeout=5m
    if($LASTEXITCODE -ne 0){throw 'Functional-test quota restoration failed.'}
    & (Join-Path $PSScriptRoot 'Wait-GatewayRoutes.ps1') -Namespace $Namespace
}
