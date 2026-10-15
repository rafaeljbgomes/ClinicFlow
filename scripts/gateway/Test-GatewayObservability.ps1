[CmdletBinding()]
param([string]$MonitoringNamespace='monitoring',[string]$IngressNamespace='ingress-system')
$ErrorActionPreference='Stop'
$PrometheusProxy="/api/v1/namespaces/$MonitoringNamespace/services/http:clinicflow-observability-prometheus:9090/proxy"
function Read-Prometheus($Suffix) {
    $Raw=& kubectl get --raw "$PrometheusProxy$Suffix"
    if($LASTEXITCODE -ne 0){throw 'Prometheus API proxy request failed.'}
    $Response=$Raw | ConvertFrom-Json
    if($Response.status -ne 'success'){throw 'Prometheus query did not succeed.'}
    return $Response.data
}
$Deadline=[DateTimeOffset]::UtcNow.AddMinutes(2)
do {
    $Targets=Read-Prometheus '/api/v1/targets?state=active'
    $GatewayTargets=@($Targets.activeTargets | Where-Object { $_.labels.namespace -eq $IngressNamespace -and $_.labels.service -eq 'clinicflow-gateway-controller-metrics' })
    if($GatewayTargets.Count -eq 1 -and $GatewayTargets[0].health -eq 'up'){break}
    Start-Sleep -Seconds 5
} while([DateTimeOffset]::UtcNow -lt $Deadline)
if($GatewayTargets.Count -ne 1 -or $GatewayTargets[0].health -ne 'up'){throw 'Gateway Prometheus target is missing or not UP.'}
Write-Host "PASS gateway scrape target UP: $($GatewayTargets[0].scrapeUrl)"
$Pods=(& kubectl get pods -n $MonitoringNamespace -l app.kubernetes.io/name=grafana -o json) | ConvertFrom-Json
if($LASTEXITCODE -ne 0 -or $Pods.items.Count -ne 1){throw 'Cannot locate the Grafana pod.'}
$Secret=(& kubectl get secret clinicflow-observability-grafana -n $MonitoringNamespace -o json) | ConvertFrom-Json
if($LASTEXITCODE -ne 0){throw 'Cannot read local Grafana credentials.'}
$User=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($Secret.data.'admin-user'))
$Password=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($Secret.data.'admin-password'))
$Credentials=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${User}:$Password"))
function Read-Grafana($Path) {
    $Raw=& kubectl exec $Pods.items[0].metadata.name -n $MonitoringNamespace -c grafana -- wget -q -O - --header="Authorization: Basic $Credentials" "http://127.0.0.1:3000$Path"
    if($LASTEXITCODE -ne 0){throw 'Grafana authenticated API request failed.'}
    return $Raw | ConvertFrom-Json
}
$Dashboard=Read-Grafana '/api/dashboards/uid/clinicflow-gateway'
if($Dashboard.dashboard.title -ne 'ClinicFlow Gateway' -or $Dashboard.dashboard.panels.Count -ne 4){throw 'Grafana did not load the four gateway panels.'}
$Datasource=Read-Grafana '/api/datasources/uid/prometheus'
if($Datasource.type -ne 'prometheus'){throw 'Gateway dashboard Prometheus datasource is unavailable.'}
foreach($Panel in $Dashboard.dashboard.panels) {
    if($Panel.datasource.uid -ne 'prometheus'){throw "Unexpected datasource in $($Panel.title)"}
    foreach($Target in $Panel.targets) {
        $Expression=$Target.expr.Replace('ingress-system',$IngressNamespace)
        $Deadline=[DateTimeOffset]::UtcNow.AddMinutes(2)
        do {
            $Data=Read-Prometheus ("/api/v1/query?query=" + [Uri]::EscapeDataString($Expression))
            $Results=@($Data.result | Where-Object { $_.value[1] -ne 'NaN' })
            if($Results.Count){break}
            Start-Sleep -Seconds 5
        } while([DateTimeOffset]::UtcNow -lt $Deadline)
        if(-not $Results.Count){throw "Gateway dashboard query has no finite samples: $($Panel.title)"}
        Write-Host "PASS dashboard query: $($Panel.title) ($($Results.Count) series)"
    }
}
Write-Host 'Gateway scraping, Grafana dashboard import/datasource, and all four panel queries passed.'
