[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('patient-service','appointment-service','clinical-service','notification-service','frontend')][string]$Component,
    [Parameter(Mandatory)][ValidateSet('Dependencies','Image','Config')][string]$Phase,
    [string]$Image
)
. (Join-Path $PSScriptRoot 'Security-Common.ps1')
Push-Location $SecurityRoot
try {
    Initialize-TrivyDatabase
    $Directory = Join-Path $SecurityReportRoot $Component
    New-Item -ItemType Directory -Force $Directory | Out-Null
    switch ($Phase) {
        Dependencies {
            if ($Component -eq 'frontend') {
                Invoke-TrivyGate $Component dependencies vulnerability @('fs', '--scanners', 'vuln', '--include-dev-deps', '--list-all-pkgs', 'frontend')
                Invoke-SecurityTool trivy @('fs','--cache-dir',$env:TRIVY_CACHE_DIR,'--format','cyclonedx','--include-dev-deps','--output',"$Directory/bom.json",'frontend')
            } else {
                $Coverage = Join-Path $SecurityRoot "$Component/target/site/jacoco/jacoco.xml"
                $Before = if (Test-Path $Coverage) { (Get-FileHash $Coverage).Hash } else { '' }
                $Maven = if ($IsWindows) { './mvnw.cmd' } else { './mvnw' }
                Invoke-SecurityTool $Maven @('-B','-ntp','-pl',":$Component",'-am','install','-DskipTests=true','-Dskip.unit.tests=true','-Djacoco.skip=true','-Dpitest.skip=true')
                Invoke-SecurityTool $Maven @('-B','-ntp','-pl',":$Component",'-am',"org.cyclonedx:cyclonedx-maven-plugin:$($SecuritySettings.cycloneDxVersion):makeBom",'-DincludeTestScope=true','-DschemaVersion=1.6','-DoutputFormat=json','-Dcyclonedx.skipAttach=true')
                if ($Before -and (Get-FileHash $Coverage).Hash -ne $Before) { throw 'Security preparation changed merged test coverage.' }
                Copy-Item "$Component/target/bom.json" "$Directory/bom.json"
                Invoke-TrivyGate $Component dependencies vulnerability @('sbom', '--list-all-pkgs', "$Directory/bom.json")
            }
        }
        Image {
            if (-not $Image) { throw 'Image is required for an image scan.' }
            $Id = & docker image inspect --format '{{.Id}}' $Image
            if ($LASTEXITCODE -ne 0 -or $Id -notmatch '^sha256:[0-9a-f]{64}$') { throw 'Unable to resolve exact local image ID.' }
            $Id | Set-Content "$Directory/image-id.txt"
            Invoke-TrivyGate $Component image vulnerability @('image','--image-src','docker','--scanners','vuln','--list-all-pkgs',$Id)
            Invoke-SecurityTool trivy @('image','--cache-dir',$env:TRIVY_CACHE_DIR,'--image-src','docker','--format','cyclonedx','--output',"$Directory/image-bom.json",$Id)
        }
        Config { Invoke-TrivyGate $Component dockerfile misconfiguration @('config', "$Component/Dockerfile") }
    }
    & trivy --version | Set-Content "$Directory/scanner-version.txt"
    Copy-Item (Join-Path $env:TRIVY_CACHE_DIR 'db/metadata.json') "$Directory/database-metadata.json"
}
finally { Pop-Location }
