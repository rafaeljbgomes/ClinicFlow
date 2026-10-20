[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$RunId,
    [ValidateSet('Dependencies','Configuration','Images','All')][string]$Phase = 'All',
    [switch]$Dependencies
)
. (Join-Path $PSScriptRoot 'Security-Common.ps1')
. (Join-Path $PSScriptRoot 'Compose-Project.ps1')
$Project = ConvertTo-ComposeProjectName -RunId $RunId
Set-IsolatedPorts -RunId $RunId
# -Dependencies keeps the original Jenkins invocation working.
if ($Dependencies) { $Phase = 'Dependencies' }
Push-Location $SecurityRoot
try {
    if ($Phase -eq 'Dependencies') {
        foreach ($Component in @('auth-service','patient-service','appointment-service','clinical-service','notification-service','frontend')) {
            & (Join-Path $PSScriptRoot 'Invoke-ComponentSecurity.ps1') -Component $Component -Phase Dependencies
        }
        return
    }
    Initialize-TrivyDatabase
    # Evaluate every target before failing so one report shows all blocking findings.
    $Violations = [System.Collections.Generic.List[string]]::new()
    function Invoke-CollectedGate([string]$Target, [string]$Kind, [string[]]$ScanArguments) {
        try { Invoke-TrivyGate platform $Target $Kind $ScanArguments }
        catch { $Violations.Add($_.Exception.Message) }
    }
    if ($Phase -in @('Configuration','All')) {
        if (-not (Test-Path 'reports/platform/auth-rendered.yaml')) { throw 'Rendered platform manifests missing.' }
        Invoke-CollectedGate manifests misconfiguration @('config','reports/platform')
        # Helm templates are covered by the rendered manifests above; this target is the deploy Dockerfiles.
        Invoke-CollectedGate dockerfiles misconfiguration @('config','--misconfig-scanners','dockerfile','deploy')
    }
    if ($Phase -in @('Images','All')) {
        # Resolve the exact images of running isolated containers, including vendor images.
        $Ids = & docker compose --file docker-compose.yml --file docker-compose.ci.yml --project-name $Project ps --all --quiet
        if ($LASTEXITCODE -ne 0 -or -not $Ids) { throw 'No isolated system containers found for image inventory.' }
        $Seen = @{}
        foreach ($Id in $Ids) {
            $Details = (& docker inspect $Id | ConvertFrom-Json)[0]
            if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect isolated container.' }
            $Service = $Details.Config.Labels.'com.docker.compose.service'
            if ($Seen.ContainsKey($Details.Image)) { continue }
            $Seen[$Details.Image] = $true
            Invoke-CollectedGate $Service vulnerability @('image','--image-src','docker','--scanners','vuln','--list-all-pkgs',$Details.Image)
        }
    }
    if ($Violations.Count) { throw ($Violations -join ' ') }
}
finally { Pop-Location }
