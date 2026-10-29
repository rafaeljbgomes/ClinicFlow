[CmdletBinding()]
param([int]$Days = 30, [switch]$Renew)
$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$Directory = Join-Path $Root 'secrets/edge'
New-Item -ItemType Directory -Force $Directory | Out-Null
$OpenSslCommand = Get-Command openssl -ErrorAction SilentlyContinue
if (-not $OpenSslCommand) { throw 'OpenSSL is required; no trust-store changes are made.' }
$OpenSsl = $OpenSslCommand.Source
function Invoke-OpenSsl { param([string[]]$Arguments)
    & $OpenSsl @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'OpenSSL certificate generation failed.' }
}
$Configuration = Join-Path $Directory 'openssl.cnf'
@('[req]','distinguished_name=dn','[dn]') | Set-Content $Configuration
$CaKey = Join-Path $Directory 'ca.key'
$CaCert = Join-Path $Directory 'ca.crt'
if ((Test-Path $CaKey) -ne (Test-Path $CaCert)) { throw 'Incomplete local CA: restore both ca.key and ca.crt before generating a certificate.' }
if (-not (Test-Path $CaKey)) {
    Invoke-OpenSsl -Arguments @('req','-config',$Configuration,'-x509','-newkey','rsa:3072','-nodes','-keyout',$CaKey,'-out',$CaCert,'-days','365','-sha256','-subj','/CN=ClinicFlow local development CA','-addext','basicConstraints=critical,CA:TRUE','-addext','keyUsage=critical,keyCertSign,cRLSign')
}
if (-not $IsWindows) {
    $env:CLINICFLOW_TLS_GROUP = (& id -g).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot determine the local certificate reader group.' }
}
$Cert = Join-Path $Directory 'edge.crt'
$Key = Join-Path $Directory 'edge.key'
# Every public gateway host: the UI, the API and Keycloak (ADR-018, ADR-027).
$Hostnames = @('clinicflow.localhost','api.clinicflow.localhost','auth.clinicflow.localhost')
if ((Test-Path $Cert) -and (Test-Path $Key) -and -not $Renew) {
    Invoke-OpenSsl -Arguments @('verify','-CAfile',$CaCert,$Cert)
    & $OpenSsl x509 -in $Cert -checkend 86400 -noout *> $null
    $Current = $LASTEXITCODE -eq 0
    foreach ($Hostname in $Hostnames) {
        & $OpenSsl x509 -in $Cert -checkhost $Hostname -noout *> $null
        if ($LASTEXITCODE -ne 0) { $Current = $false }
    }
    if ($Current) {
        if (-not $IsWindows) { & chmod 600 $CaKey; & chmod 640 $Key; if ($LASTEXITCODE -ne 0) { throw 'Unable to restrict key permissions.' } }
        Write-Host "Existing certificate retained: $Cert"
        return
    }
    # Only the leaf is re-issued, so a CA the browser already trusts stays valid.
    Write-Host 'The existing certificate expires within a day or lacks a gateway host; issuing a new one from the same CA.'
}
Invoke-OpenSsl -Arguments @('req','-config',$Configuration,'-new','-newkey','rsa:2048','-nodes','-keyout',$Key,'-out',(Join-Path $Directory 'edge.csr'),'-subj','/CN=clinicflow.localhost')
$Extensions = Join-Path $Directory 'edge.ext'
@('basicConstraints=critical,CA:FALSE','keyUsage=critical,digitalSignature,keyEncipherment','extendedKeyUsage=serverAuth',"subjectAltName=$(($Hostnames | ForEach-Object { "DNS:$_" }) -join ',')") | Set-Content $Extensions
Invoke-OpenSsl -Arguments @('x509','-req','-in',(Join-Path $Directory 'edge.csr'),'-CA',$CaCert,'-CAkey',$CaKey,'-CAcreateserial','-out',$Cert,'-days',[string]$Days,'-sha256','-extfile',$Extensions)
if (-not $IsWindows) { & chmod 600 $CaKey; & chmod 640 $Key; if ($LASTEXITCODE -ne 0) { throw 'Unable to restrict key permissions.' } }
Write-Host "Created $Cert. Trust ca.crt manually in your browser if desired; keys remain outside Git."
