[CmdletBinding()]
param([Parameter(Mandatory)][string]$PatientImageTag,[string]$Namespace='clinicflow',[string]$IngressNamespace='ingress-system')
$ErrorActionPreference='Stop'
$Root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
function Snapshot {
    $Result=@{}
    foreach($Ns in @($Namespace,$IngressNamespace)) {
        $Deployments=(& kubectl get deployments -n $Ns -o json)|ConvertFrom-Json
        if($LASTEXITCODE -ne 0){throw 'Cannot snapshot deployment images.'}
        foreach($Deployment in $Deployments.items){$Result["$Ns/$($Deployment.metadata.name)"]=$Deployment.spec.template.spec.containers.image -join ','}
    }
    return $Result
}
function Revision($Release,$Ns){
    $Status=(& helm status $Release -n $Ns -o json)|ConvertFrom-Json
    if($LASTEXITCODE -ne 0){throw "Cannot inspect $Release"}
    return $Status.version
}
$Before=Snapshot
$ControllerRevision=Revision clinicflow-gateway-controller $IngressNamespace
$GatewayRevision=Revision clinicflow-gateway $Namespace
$PatientRevision=Revision clinicflow-patient $Namespace
$GatewayStatus=(& helm get values clinicflow-gateway -n $Namespace -a -o json)|ConvertFrom-Json
if($LASTEXITCODE -ne 0){throw 'Cannot inspect gateway configuration.'}
$TemporaryAverage=[int]$GatewayStatus.rateLimits.public.average+1
& helm upgrade clinicflow-gateway (Join-Path $Root 'deploy/helm/gateway') -n $Namespace --reuse-values --set "rateLimits.public.average=$TemporaryAverage" --rollback-on-failure --server-side=false --wait=watcher --timeout=5m
if($LASTEXITCODE -ne 0){throw 'Gateway configuration upgrade failed.'}
& (Join-Path $PSScriptRoot 'Wait-GatewayRoutes.ps1') -Namespace $Namespace
& helm rollback clinicflow-gateway $GatewayRevision -n $Namespace --server-side=false --wait=watcher --timeout=5m
if($LASTEXITCODE -ne 0){throw 'Gateway configuration rollback failed.'}
& (Join-Path $PSScriptRoot 'Wait-GatewayRoutes.ps1') -Namespace $Namespace
$GatewayAfterRollback=Revision clinicflow-gateway $Namespace
$RestoredValues=(& helm get values clinicflow-gateway -n $Namespace -a -o json)|ConvertFrom-Json
if($LASTEXITCODE -ne 0 -or $RestoredValues.rateLimits.public.average -ne $GatewayStatus.rateLimits.public.average){throw 'Gateway rollback did not restore the original rate configuration.'}
& (Join-Path $Root 'scripts/deploy-local-kubernetes.ps1') -Component Patient -SkipBuild -ImageTag $PatientImageTag -Namespace $Namespace -IngressNamespace $IngressNamespace
$AfterUpgrade=Snapshot
if($AfterUpgrade["$Namespace/patient-service"] -ne "clinicflow/patient-service:$PatientImageTag"){throw 'Patient upgrade did not select the supplied image.'}
foreach($Key in $Before.Keys){if($Key -ne "$Namespace/patient-service" -and $Before[$Key] -ne $AfterUpgrade[$Key]){throw "Patient upgrade changed unrelated image $Key"}}
if((Revision clinicflow-gateway-controller $IngressNamespace) -ne $ControllerRevision){throw 'Patient upgrade changed controller release.'}
if((Revision clinicflow-gateway $Namespace) -ne $GatewayAfterRollback){throw 'Patient upgrade changed gateway release.'}
& helm rollback clinicflow-patient $PatientRevision -n $Namespace --server-side=false --wait=watcher --timeout=5m
if($LASTEXITCODE -ne 0){throw 'Patient rollback failed.'}
$After=Snapshot
foreach($Key in $Before.Keys){if($Before[$Key] -ne $After[$Key]){throw "Image rollback mismatch: $Key"}}
if((Revision clinicflow-gateway $Namespace) -ne $GatewayAfterRollback){throw 'Patient rollback changed gateway release.'}
& (Join-Path $PSScriptRoot 'Test-GatewayTraffic.ps1') -IngressNamespace $IngressNamespace
Write-Host 'Gateway configuration and patient-only upgrade/rollback passed; unrelated images/releases were preserved. CRDs were not rolled back.'
