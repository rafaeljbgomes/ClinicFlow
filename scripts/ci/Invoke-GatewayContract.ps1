[CmdletBinding()]
param([Parameter(Mandatory)][string]$RunId,[string]$RuntimeHost='127.0.0.1')
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Compose-Project.ps1')
Set-IsolatedPorts -RunId $RunId
$Root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$Reports=Join-Path $Root 'reports/gateway'
New-Item -ItemType Directory -Force $Reports | Out-Null
$SavedRuntimeHost=$env:GATEWAY_TEST_RUNTIME_HOST
$env:GATEWAY_TEST_RUNTIME_HOST=$RuntimeHost
$SavedTls=$env:GATEWAY_TEST_TLS
$SavedHttp=$env:GATEWAY_TEST_HTTP_PORT
$SavedHttps=$env:GATEWAY_TEST_HTTPS_PORT
Push-Location $Root
try {
    $env:GATEWAY_TEST_HTTP_PORT=[string]([int]$env:CLINICFLOW_GATEWAY_HTTP_PORT-5)
    $env:GATEWAY_TEST_HTTPS_PORT=[string]([int]$env:CLINICFLOW_GATEWAY_HTTP_PORT-4)
    foreach($Tls in @('false','true')) {
        $env:GATEWAY_TEST_TLS=$Tls
        $Output=& node ./scripts/gateway/test-contract.cjs 2>&1
        $ExitCode=$LASTEXITCODE
        $Output | Set-Content (Join-Path $Reports "contract-tls-$Tls.txt")
        $Output | Write-Host
        if($ExitCode -ne 0){throw "Gateway contract acceptance failed (TLS=$Tls)."}
    }
}
finally {
    $env:GATEWAY_TEST_RUNTIME_HOST=$SavedRuntimeHost
    $env:GATEWAY_TEST_TLS=$SavedTls
    $env:GATEWAY_TEST_HTTP_PORT=$SavedHttp
    $env:GATEWAY_TEST_HTTPS_PORT=$SavedHttps
    Pop-Location
}
