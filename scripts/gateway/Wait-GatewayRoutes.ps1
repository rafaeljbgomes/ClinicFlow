[CmdletBinding()]
param([string]$Namespace='clinicflow',[string]$Release='clinicflow-gateway',[int]$TimeoutSeconds=180)
$ErrorActionPreference='Stop'
# Every HTTPRoute the installed gateway release declares must attach, so a new route needs no edit here.
$Manifest=(& helm get manifest $Release -n $Namespace) -join "`n"
if ($LASTEXITCODE -ne 0) { throw "Cannot read the $Release release manifest." }
$ExpectedRoutes=[regex]::Matches($Manifest,'(?m)^kind: HTTPRoute\s*$').Count
if ($ExpectedRoutes -lt 1) { throw "The $Release release declares no HTTPRoutes." }
$Deadline=[DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
function Test-CurrentConditions($Conditions,$Generation,$Types) {
    foreach ($Type in $Types) {
        $Condition=@($Conditions | Where-Object { $_.type -eq $Type -and $_.status -eq 'True' -and $_.observedGeneration -eq $Generation })
        if ($Condition.Count -ne 1) { return $false }
    }
    return $true
}
do {
    $Gateway=(& kubectl get gateway clinicflow-gateway -n $Namespace -o json) | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read application Gateway.' }
    $Ready=Test-CurrentConditions $Gateway.status.conditions $Gateway.metadata.generation @('Accepted','Programmed')
    foreach ($Listener in $Gateway.status.listeners) {
        $Ready=$Ready -and (Test-CurrentConditions $Listener.conditions $Gateway.metadata.generation @('Accepted','ResolvedRefs','Programmed'))
    }
    $Routes=(& kubectl get httproutes -n $Namespace -o json) | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read HTTPRoutes.' }
    $Attached=@($Routes.items | Where-Object { $_.spec.parentRefs.name -contains 'clinicflow-gateway' })
    $Ready=$Ready -and $Attached.Count -ge $ExpectedRoutes
    foreach ($Route in $Attached) {
        $Parents=@($Route.status.parents | Where-Object { $_.parentRef.name -eq 'clinicflow-gateway' })
        $Ready=$Ready -and $Parents.Count -gt 0
        foreach ($Parent in $Parents) { $Ready=$Ready -and (Test-CurrentConditions $Parent.conditions $Route.metadata.generation @('Accepted','ResolvedRefs')) }
    }
    if ($Ready) { Write-Host "Gateway and $($Attached.Count) routes accepted at their current generations."; return }
    Start-Sleep -Seconds 2
} while ([DateTimeOffset]::UtcNow -lt $Deadline)
& kubectl get gateway,httproute -n $Namespace -o yaml
throw 'Gateway acceptance/reference resolution did not complete at the current generation.'
