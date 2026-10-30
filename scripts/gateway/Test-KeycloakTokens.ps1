[CmdletBinding()]
param([string]$Namespace='clinicflow',[int]$KeycloakAdminPort=8180)
# Runs test-keycloak-tokens.cjs with the admin port-forward it needs for its temporary realm (ADR-027).
$ErrorActionPreference='Stop'
$Probe=[Net.Sockets.TcpClient]::new()
try { if($Probe.ConnectAsync('127.0.0.1',$KeycloakAdminPort).Wait(300) -and $Probe.Connected){throw "Port $KeycloakAdminPort is in use; stop the Compose Keycloak or the process holding it."} } finally {$Probe.Dispose()}
$PortForward=Start-Process kubectl -ArgumentList @('port-forward','svc/keycloak',"$($KeycloakAdminPort):8080",'-n',$Namespace) -PassThru -NoNewWindow -RedirectStandardOutput ([IO.Path]::GetTempFileName())
try {
    $Deadline=[DateTimeOffset]::UtcNow.AddSeconds(30)
    do {
        Start-Sleep -Seconds 1
        try { $Ready=(Invoke-WebRequest "http://localhost:$KeycloakAdminPort/realms/clinicflow/.well-known/openid-configuration" -TimeoutSec 3).StatusCode -eq 200 } catch { $Ready=$false }
    } until($Ready -or [DateTimeOffset]::UtcNow -gt $Deadline)
    if(-not $Ready){throw 'The Keycloak port-forward did not become ready.'}
    $env:KEYCLOAK_ADMIN_URL="http://localhost:$KeycloakAdminPort"
    & node (Join-Path $PSScriptRoot 'test-keycloak-tokens.cjs')
    if($LASTEXITCODE -ne 0){throw 'Keycloak issuer acceptance failed.'}
} finally {
    Remove-Item Env:KEYCLOAK_ADMIN_URL -ErrorAction SilentlyContinue
    Stop-Process -Id $PortForward.Id -ErrorAction SilentlyContinue
}
