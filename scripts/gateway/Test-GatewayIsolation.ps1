[CmdletBinding()]
param([string]$Namespace='clinicflow',[string]$IngressNamespace='ingress-system',[string]$MonitoringNamespace='monitoring')
$ErrorActionPreference='Stop'
$Run='edge-probe-'+[Guid]::NewGuid().ToString('N').Substring(0,10)
$Image='node:24.19.0-alpine@sha256:d32cdf619f63fe0471182d08996dd516c6275bb5fd31ae06e55a570bd9e1ad43'
$Probes=@(
    @{Name='denied';Namespace=$Namespace;Labels=@{}},
    @{Name='spoofed-gateway';Namespace=$Namespace;Labels=@{'clinicflow.io/edge'='gateway'}},
    @{Name='frontend';Namespace=$Namespace;Labels=@{'app.kubernetes.io/component'='frontend'}},
    @{Name='gateway';Namespace=$IngressNamespace;Labels=@{'clinicflow.io/edge'='gateway'}},
    @{Name='monitoring';Namespace=$MonitoringNamespace;Labels=@{'app.kubernetes.io/name'='prometheus'}},
    @{Name='egress-sink';Namespace=$Namespace;Labels=@{'app.kubernetes.io/component'='postgres';'clinicflow.io/sink'=$Run};Command="for(const port of [5432,5672])require('node:http').createServer((q,r)=>r.end('ok')).listen(port,'0.0.0.0')"}
)
function Apply-Object($Object) {
    $Object | ConvertTo-Json -Depth 15 | & kubectl apply -f -
    if ($LASTEXITCODE -ne 0) { throw 'Probe resource creation failed.' }
}
function Probe($Name,$Url,$Allowed,$Status) {
    $Source=$Probes | Where-Object Name -eq $Name
    $Code="fetch(process.argv[1],{signal:AbortSignal.timeout(4000)}).then(r=>console.log(r.status)).catch(()=>process.exit(9))"
    $Actual=& kubectl exec "$Run-$Name" -n $Source.Namespace -- node -e $Code $Url 2>$null
    $Connected=$LASTEXITCODE -eq 0
    if ($Connected -ne $Allowed -or ($Allowed -and $Actual -ne $Status)) { throw "Isolation FAILED: $Name -> $Url returned '$Actual'; allowed=$Allowed expected=$Status" }
    Write-Host "PASS $Name -> $Url (allowed=$Allowed)"
}
try {
    foreach ($TargetNamespace in @($Namespace,$IngressNamespace,$MonitoringNamespace) | Sort-Object -Unique) {
        # Give only probes unrestricted EGRESS so a denied result proves destination ingress, not missing probe egress.
        Apply-Object @{apiVersion='networking.k8s.io/v1';kind='NetworkPolicy';metadata=@{name=$Run;namespace=$TargetNamespace;labels=@{'clinicflow.io/probe'=$Run}};spec=@{podSelector=@{matchLabels=@{'clinicflow.io/probe'=$Run}};policyTypes=@('Egress');egress=@(@{})}}
    }
    foreach ($Source in $Probes) {
        $Labels=@{'clinicflow.io/probe'=$Run};foreach ($Key in $Source.Labels.Keys) { $Labels[$Key]=$Source.Labels[$Key] }
        $Code=if($Source.ContainsKey('Command')){$Source.Command}else{'setInterval(()=>{},10000)'}
        Apply-Object @{apiVersion='v1';kind='Pod';metadata=@{name="$Run-$($Source.Name)";namespace=$Source.Namespace;labels=$Labels};spec=@{automountServiceAccountToken=$false;restartPolicy='Never';containers=@(@{name='probe';image=$Image;command=@('node','-e',$Code);securityContext=@{allowPrivilegeEscalation=$false;runAsNonRoot=$true;runAsUser=1000;capabilities=@{drop=@('ALL')}}})}}
        & kubectl wait --for=condition=Ready "pod/$Run-$($Source.Name)" -n $Source.Namespace --timeout=90s
        if ($LASTEXITCODE -ne 0) { throw 'Probe pod did not become ready.' }
    }
    foreach ($Backend in @(@('auth-service',8081,'/users/me'),@('patient-service',8082,'/patients'),@('appointment-service',8083,'/appointments'),@('notification-service',8084,'/notifications'),@('clinical-service',8085,'/clinical-cases'))) {
        $Url="http://$($Backend[0]).$Namespace.svc.cluster.local:$($Backend[1])$($Backend[2])"
        Probe gateway $Url $true '401'
        Probe denied $Url $false ''
        Probe spoofed-gateway $Url $false ''
        Probe frontend $Url $false ''
        $Health="http://$($Backend[0]).$Namespace.svc.cluster.local:9000/actuator/health"
        Probe frontend $Health $true '200'
        Probe monitoring $Health $true '200'
        Probe denied $Health $false ''
        Probe gateway $Health $false ''
    }
    $Private="http://clinicflow-gateway-api.$IngressNamespace.svc.cluster.local:8080/api/v1/patients"
    Probe frontend $Private $true '401'
    Probe denied $Private $false ''
    Probe monitoring $Private $false ''
    Probe gateway "http://frontend.$Namespace.svc.cluster.local:3000/login" $true '200'
    Probe denied "http://frontend.$Namespace.svc.cluster.local:3000/login" $false ''
    Probe monitoring "http://clinicflow-gateway-controller-metrics.$IngressNamespace.svc.cluster.local:9100/metrics" $true '200'
    Probe denied "http://clinicflow-gateway-controller-metrics.$IngressNamespace.svc.cluster.local:9100/metrics" $false ''
    $Controller=(& kubectl get pods -n $IngressNamespace -l clinicflow.io/edge=gateway,app.kubernetes.io/name=traefik -o json | ConvertFrom-Json).items[0].metadata.name
    & kubectl exec $Controller -n $IngressNamespace -- wget -T 4 -q -O /dev/null "http://frontend.$Namespace.svc.cluster.local:3000/login"
    if ($LASTEXITCODE -ne 0) { throw 'Real controller cannot reach its allowed frontend/DNS positive control.' }
    & kubectl exec $Controller -n $IngressNamespace -- sh -c 'command -v nc' 
    if ($LASTEXITCODE -ne 0) { throw 'TCP probe tool is unavailable; egress isolation cannot be inferred.' }
    # Open ONLY the temporary sink's ingress, with a frontend positive control.
    # This separates controller egress denial from the real DB/broker ingress rules.
    Apply-Object @{apiVersion='networking.k8s.io/v1';kind='NetworkPolicy';metadata=@{name="$Run-sink";namespace=$Namespace;labels=@{'clinicflow.io/probe'=$Run}};spec=@{podSelector=@{matchLabels=@{'clinicflow.io/sink'=$Run}};policyTypes=@('Ingress');ingress=@(@{})}}
    $Sink=(& kubectl get pod "$Run-egress-sink" -n $Namespace -o json | ConvertFrom-Json).status.podIP
    foreach($Port in @(5432,5672)) {
        Probe frontend "http://${Sink}:$Port/" $true '200'
        & kubectl exec $Controller -n $IngressNamespace -- nc -z -w 4 $Sink $Port 2>$null
        if($LASTEXITCODE -eq 0){throw "Controller egress unexpectedly reached ingress-open sink on port $Port"}
        Write-Host "PASS real controller egress -> ingress-open sink:$Port (allowed=False)"
    }
    foreach ($Destination in @(@("postgres.$Namespace.svc.cluster.local",5432),@("rabbitmq.$Namespace.svc.cluster.local",5672))) {
        & kubectl exec $Controller -n $IngressNamespace -- nc -z -w 4 $Destination[0] $Destination[1] 2>$null
        if ($LASTEXITCODE -eq 0) { throw "Controller unexpectedly reached $($Destination[0]):$($Destination[1])" }
    }
    Write-Host 'Isolation traffic acceptance passed in this cluster. Retain this output and CNI/version evidence before closing issue #13.'
}
finally {
    foreach ($TargetNamespace in @($Namespace,$IngressNamespace,$MonitoringNamespace) | Sort-Object -Unique) {
        & kubectl delete pods,networkpolicies -n $TargetNamespace -l "clinicflow.io/probe=$Run" --ignore-not-found --wait=false
    }
}
