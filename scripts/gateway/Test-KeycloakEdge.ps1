[CmdletBinding()]
param([string]$IngressNamespace='ingress-system',[string]$Address='127.0.0.1',[string]$AuthHost='auth.clinicflow.localhost',[string]$PublicUrl='https://clinicflow.localhost')
# Keycloak at the edge (ADR-027, #89): what the auth host serves, what it refuses, and what it keeps out
# of logs. Run it with the gateway's normal quotas; the last check spends the login burst on purpose.
$ErrorActionPreference='Stop'
$Root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$Ca=Join-Path $Root 'secrets/edge/ca.crt'
if (-not (Test-Path $Ca)) { throw 'Local CA certificate is required to verify HTTPS.' }
$Curl=if (Get-Command curl.exe -ErrorAction SilentlyContinue) { 'curl.exe' } else { 'curl' }
$Sink=if ($IsWindows) { 'NUL' } else { '/dev/null' }
$Issuer="https://$AuthHost/realms/clinicflow"
$Tls=@('--silent','--show-error','--max-time','20','--resolve',"${AuthHost}:443:$Address",'--cacert',$Ca)
if ($IsWindows) { $Tls+='--ssl-revoke-best-effort' }

function Get-Status([string]$Path,[string]$Method='GET') {
    $Status=& $Curl @Tls --request $Method --output $Sink --write-out '%{http_code}' "https://$AuthHost$Path"
    if ($LASTEXITCODE -ne 0) { throw "Request to $Path failed." }
    return $Status
}
function Get-Discovery([string[]]$Headers=@()) {
    $Body=& $Curl @Tls @Headers --fail "https://$AuthHost/realms/clinicflow/.well-known/openid-configuration"
    if ($LASTEXITCODE -ne 0) { throw 'Discovery through the gateway failed.' }
    return ($Body -join "`n") | ConvertFrom-Json
}
function Assert-PublicEndpoints($Discovery,[string]$Case) {
    if ($Discovery.issuer -ne $Issuer) { throw "${Case}: issuer '$($Discovery.issuer)' is not $Issuer." }
    foreach ($Name in @('authorization_endpoint','token_endpoint','jwks_uri','end_session_endpoint','userinfo_endpoint')) {
        $Url=$Discovery.$Name
        if (-not $Url.StartsWith("https://$AuthHost/realms/clinicflow/") -or $Url -match ':8443') { throw "${Case}: $Name '$Url' is not on the public origin." }
    }
    Write-Host "PASS $Case"
}

# Discovery carries the public issuer and https endpoints without Traefik's internal port (xforwarded).
Assert-PublicEndpoints (Get-Discovery) 'discovery through the gateway'
# Traefik replaces client-sent X-Forwarded-* headers, so a forged host changes nothing.
Assert-PublicEndpoints (Get-Discovery @('--header','X-Forwarded-Host: evil.example','--header','X-Forwarded-Proto: http')) 'forged X-Forwarded-* headers ignored'

$Headers=& $Curl @Tls --head "https://$AuthHost/realms/clinicflow/.well-known/openid-configuration"
if (-not ($Headers -match '^strict-transport-security:') -or -not ($Headers -match '^x-content-type-options:\s*nosniff')) { throw 'The auth host lacks HSTS or nosniff.' }
Write-Host 'PASS secure headers on the auth host'

$Redirect=& $Curl --silent --show-error --max-time 10 --output $Sink --write-out '%{http_code}' --resolve "${AuthHost}:80:$Address" "http://$AuthHost/realms/clinicflow/.well-known/openid-configuration"
if ($Redirect -ne '308') { throw "HTTP on the auth host returned '$Redirect', expected 308." }
Write-Host 'PASS http -> https 308'

# Everything outside the clinicflow realm's browser and OIDC paths is unrouted.
foreach ($Case in @(@('/admin/','GET'),@('/admin/master/console/','GET'),@('/admin/realms/clinicflow/users','GET'),@('/realms/master/.well-known/openid-configuration','GET'),@('/realms/master/protocol/openid-connect/token','POST'),@('/realms/clinicflow/account','GET'),@('/realms/clinicflow/clients-registrations/openid-connect','POST'),@('/realms/clinicflow/broker/x/endpoint','GET'),@('/metrics','GET'),@('/health/ready','GET'),@('/','GET'))) {
    $Status=Get-Status $Case[0] $Case[1]
    if ($Status -ne '404') { throw "$($Case[1]) $($Case[0]) returned $Status, expected 404 from the edge." }
    Write-Host "PASS $($Case[1]) $($Case[0]) -> 404"
}

# The login page and one theme asset it references load through the gateway. The run marker travels in
# the query, which the access log must not record.
$Marker='kc-edge-'+[Guid]::NewGuid().ToString('N').Substring(0,12)
$Since=[DateTimeOffset]::UtcNow.AddSeconds(-5).ToString('o')
$Query="client_id=clinicflow-bff&response_type=code&scope=openid&state=$Marker&redirect_uri=$([Uri]::EscapeDataString("$PublicUrl/api/auth/callback"))&code_challenge=$Marker$Marker$Marker$Marker&code_challenge_method=S256"
$Page=(& $Curl @Tls --fail "https://$AuthHost/realms/clinicflow/protocol/openid-connect/auth?$Query") -join "`n"
if ($LASTEXITCODE -ne 0 -or $Page -notmatch 'id="kc-form-login"') { throw 'The login page did not load through the gateway.' }
Write-Host 'PASS login page'
$Asset=[regex]::Match($Page,'(?:href|src)="(/resources/[^"]+)"').Groups[1].Value
if (-not $Asset) { throw 'The login page references no theme resource.' }
# Compressed, as browsers ask: Keycloak caches gzipped resources on disk and answers 404 when it cannot.
$Status=& $Curl @Tls --compressed --output $Sink --write-out '%{http_code}' "https://$AuthHost$([Net.WebUtility]::HtmlDecode($Asset))"
if ($Status -ne '200') { throw "Theme asset $Asset returned $Status with gzip accepted." }
Write-Host "PASS compressed theme asset $Asset"
Start-Sleep -Seconds 2
$Controller=(& kubectl get pods -n $IngressNamespace -l clinicflow.io/edge=gateway,app.kubernetes.io/name=traefik -o json | ConvertFrom-Json).items[0].metadata.name
$Log=(& kubectl logs $Controller -n $IngressNamespace --since-time=$Since) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'Cannot read the controller access log.' }
if ($Log -notmatch 'identity-oidc') { throw 'The authorize request is missing from the access log; the redaction check would prove nothing.' }
if ($Log -match [regex]::Escape($Marker) -or $Log -match 'session_code=|[?&]code=') { throw 'The access log recorded query values from the identity routes.' }
Write-Host 'PASS access log keeps no query values'

# Last, because it spends the bucket: the login limit (burst 5) applies to the form posts.
$Codes=foreach ($Attempt in 1..6) { Get-Status '/realms/clinicflow/login-actions/authenticate' 'POST' }
if ($Codes -notcontains '429') { throw "Six fast login posts returned $($Codes -join ','), with no 429." }
Write-Host "PASS login-actions rate limit ($($Codes -join ','))"
Write-Host 'Keycloak edge acceptance passed.'
