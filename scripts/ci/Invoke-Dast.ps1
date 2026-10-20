[CmdletBinding()]
param([Parameter(Mandatory)][string]$RunId, [switch]$FullScan, [switch]$SmokeOnly)
. (Join-Path $PSScriptRoot 'Security-Common.ps1')
. (Join-Path $PSScriptRoot 'Compose-Project.ps1')
$Project = ConvertTo-ComposeProjectName -RunId "$RunId-security"
$Container = "$Project-zap"
Set-IsolatedPorts -RunId "$RunId-security"
$env:CLINICFLOW_SECURITY_TAG = $Project
$Context = Get-Content -Raw (Join-Path $SecurityReportRoot 'context.json') | ConvertFrom-Json
$env:DAST_COMPONENTS = if ($Context.fullSystem -or $FullScan) { 'auth-service,patient-service,appointment-service,clinical-service,notification-service,frontend' } else { (@('auth-service') + @($Context.components) | Select-Object -Unique) -join ',' }
$env:DAST_FULL_SCAN = $FullScan.IsPresent.ToString().ToLowerInvariant()
$env:DAST_SMOKE_ONLY = $SmokeOnly.IsPresent.ToString().ToLowerInvariant()
$Compose = @('compose','--file','docker-compose.yml','--file','docker-compose.ci.yml','--file','docker-compose.security.yml','--project-name',$Project,'--profile','security')
$Directory = Join-Path $SecurityReportRoot 'dast'
New-Item -ItemType Directory -Force $Directory | Out-Null
Push-Location $SecurityRoot
try {
    Invoke-SecurityTool docker ($Compose + @('build','postgres-bootstrap','gateway','auth-service','patient-service','appointment-service','clinical-service','notification-service','frontend','zap'))
    Invoke-SecurityTool docker ($Compose + @('up','--detach','--no-build','gateway','auth-service','patient-service','appointment-service','clinical-service','notification-service','frontend'))
    foreach ($Service in @('auth-service','patient-service','appointment-service','clinical-service','notification-service')) {
        $Deadline = [DateTimeOffset]::UtcNow.AddSeconds(240)
        do {
            & docker @Compose exec -T gateway wget -T 5 -q -O /dev/null "http://${Service}:9000/actuator/health" 2>$null
            if ($LASTEXITCODE -eq 0) { break }
            Start-Sleep -Seconds 2
        } while ([DateTimeOffset]::UtcNow -lt $Deadline)
        if ($LASTEXITCODE -ne 0) { throw "$Service did not become ready on its private management port." }
    }
    & docker @Compose run --no-deps --name $Container zap
    $Code = $LASTEXITCODE
    & docker cp "${Container}:/zap/wrk/reports/." $Directory
    if ($LASTEXITCODE -ne 0 -or $Code -ne 0) { throw 'DAST failed or its reports could not be collected.' }
    if ($SmokeOnly) { throw 'Smoke-only discovery is diagnostic and cannot satisfy the DAST gate.' }
    & node (Join-Path $PSScriptRoot 'security-policy.cjs') evaluate-zap "$Directory/zap.json"
    if ($LASTEXITCODE -eq 2) { Register-SecurityViolation 'ZAP reported unexcepted High-risk alerts.' }
    elseif ($LASTEXITCODE -ne 0) { throw 'ZAP did not produce a complete authenticated scan.' }
}
finally {
    & (Join-Path $PSScriptRoot 'Stop-SecurityCompose.ps1') -RunId $RunId
    Pop-Location
}
