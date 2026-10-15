[CmdletBinding()]
param([string]$IngressNamespace='ingress-system',[string]$Address='127.0.0.1',[int]$TimeoutSeconds=45)
$ErrorActionPreference='Stop'
$Root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$Ca=Join-Path $Root 'secrets/edge/ca.crt'
if (-not (Test-Path $Ca)) { throw 'Local CA certificate is required to verify HTTPS.' }
$Service=(& kubectl get service clinicflow-gateway-controller -n $IngressNamespace -o json) | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect public gateway Service.' }
if (-not $Service.status.loadBalancer.ingress) { throw 'Gateway LoadBalancer has no ingress address.' }
$Sink=if ($IsWindows) { 'NUL' } else { '/dev/null' }
$Curl=if (Get-Command curl.exe -ErrorAction SilentlyContinue) { 'curl.exe' } else { 'curl' }
foreach ($Case in @(@{HostName='clinicflow.localhost';Path='/login';Status='200'},@{HostName='api.clinicflow.localhost';Path='/api/v1/patients';Status='401'},@{HostName='api.clinicflow.localhost';Path='/actuator/health';Status='404'},@{HostName='unknown.clinicflow.localhost';Path='/';Status='404'})) {
    $Arguments=@('--silent','--show-error','--max-time','20','--output',$Sink,'--write-out','%{http_code}','--resolve',"$($Case.HostName):443:$Address")
    # Unknown hostname has no certificate identity; test its HTTP listener instead.
    if ($Case.HostName -like 'unknown*') { $Arguments=@('--silent','--show-error','--max-time','10','--output',$Sink,'--write-out','%{http_code}','--resolve',"$($Case.HostName):80:$Address"); $Url="http://$($Case.HostName)$($Case.Path)" }
    else { $Arguments+=@('--cacert',$Ca); if ($IsWindows) { $Arguments+='--ssl-revoke-best-effort' };  $Url="https://$($Case.HostName)$($Case.Path)" }
    # Gateway conditions can precede publication of the CRD provider's middlewares.
    # Require real traffic to converge within a bounded deadline as well.
    $Deadline=[DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $Status=& $Curl @Arguments $Url
        $Succeeded=$LASTEXITCODE -eq 0 -and $Status -eq $Case.Status
        if($Succeeded){break}
        Start-Sleep -Seconds 2
    } while([DateTimeOffset]::UtcNow -lt $Deadline)
    if (-not $Succeeded) { throw "Traffic check failed: $Url returned '$Status', expected $($Case.Status)." }
}
$Redirect=& $Curl --silent --show-error --max-time 10 --output $Sink --write-out '%{http_code}' --request POST --resolve "api.clinicflow.localhost:80:$Address" http://api.clinicflow.localhost/api/v1/auth/login
if ($LASTEXITCODE -ne 0 -or $Redirect -ne '308') { throw "Mutation HTTP-to-HTTPS redirect did not return 308: '$Redirect'" }
Write-Host 'Gateway HTTPS, business routing, authentication, management isolation, and method-preserving redirect checks passed.' 
