[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('patient-service','appointment-service','clinical-service','notification-service','frontend')][string]$Component)
. (Join-Path $PSScriptRoot 'Security-Common.ps1')
if (-not $env:SONAR_TOKEN -or -not $env:SONAR_ORGANIZATION) { throw 'Sonar credentials and organization are required.' }
$Context = Get-Content -Raw (Join-Path $SecurityReportRoot 'context.json') | ConvertFrom-Json
$Key = $SecuritySettings.sonar.projectPrefix + $Component.Replace('-service','')
$Directory = Join-Path $SecurityReportRoot $Component
New-Item -ItemType Directory -Force $Directory | Out-Null
$MetadataFile = Join-Path $Directory 'sonar-task.txt'
if (Test-Path $MetadataFile) { Remove-Item -LiteralPath $MetadataFile -Force }
$Arguments = @("-Dsonar.scanner.metadataFilePath=$MetadataFile","-Dsonar.host.url=$($SecuritySettings.sonar.hostUrl)","-Dsonar.organization=$env:SONAR_ORGANIZATION","-Dsonar.projectKey=$Key",'-Dsonar.qualitygate.wait=true',"-Dsonar.qualitygate.timeout=$($SecuritySettings.sonar.timeoutSeconds)","-Dsonar.scm.revision=$($Context.sha)")
if ($Context.pullRequest) {
    $Arguments += @("-Dsonar.pullrequest.key=$env:CHANGE_ID","-Dsonar.pullrequest.branch=$env:CHANGE_BRANCH",'-Dsonar.pullrequest.base=main')
    # A stale main analysis must never silently redefine what counts as new code.
    $Headers = @{ Authorization = "Bearer $env:SONAR_TOKEN" }
    $Uri = "$($SecuritySettings.sonar.hostUrl)/api/project_analyses/search?project=$Key&branch=main&ps=1"
    $Analyses = Invoke-RestMethod -Uri $Uri -Headers $Headers -TimeoutSec 30
    if (-not $Analyses.analyses) { throw 'Sonar main baseline missing; run the component main job first.' }
    $Revision = $Analyses.analyses[0].revision
    & git diff --quiet $Revision $Context.targetSha -- $Component pom.xml event-contracts scripts/ci .ci
    if ($LASTEXITCODE -ne 0) {
        throw 'Sonar main baseline is stale. Run this component main job, then rerun the PR check.'
    }
} else { $Arguments += '-Dsonar.branch.name=main' }
Push-Location $SecurityRoot
try {
    if ($Component -eq 'frontend') {
        if (-not (Test-Path 'frontend/coverage/lcov.info')) { throw 'Frontend LCOV report missing.' }
        Push-Location frontend
        try {
            & sonar-scanner @Arguments '-Dsonar.sources=src' '-Dsonar.tests=tests' '-Dsonar.javascript.lcov.reportPaths=coverage/lcov.info' '-Dsonar.exclusions=**/*.d.ts,**/node_modules/**,**/.next/**'
            $Code = $LASTEXITCODE
        } finally { Pop-Location }
    } else {
        if (-not (Test-Path "$Component/target/site/jacoco/jacoco.xml")) { throw 'Merged JaCoCo XML missing.' }
        $Maven = if ($IsWindows) { './mvnw.cmd' } else { './mvnw' }
        & $Maven -B -ntp -pl ":$Component" -am "org.sonarsource.scanner.maven:sonar-maven-plugin:$($SecuritySettings.sonar.mavenPluginVersion):sonar" @Arguments "-Dsonar.coverage.jacoco.xmlReportPaths=$SecurityRoot/$Component/target/site/jacoco/jacoco.xml" '-Dsonar.coverage.exclusions=**/*Application.java,**/adapters/inbound/rest/dto/**,**/*MapperImpl.java'
        $Code = $LASTEXITCODE
    }
    if ($Code -ne 0) { Register-SecurityViolation "Sonar analysis or quality gate failed for $Component (exit $Code)." }
    else {
        if (-not (Test-Path $MetadataFile)) { throw 'Sonar task identity missing; no verifiable quality gate result.' }
        $Properties = ConvertFrom-StringData (Get-Content -Raw $MetadataFile)
        if (-not $Properties.ceTaskId) { throw 'Sonar compute task identity missing.' }
        $Headers = @{ Authorization = "Bearer $env:SONAR_TOKEN" }
        $Task = Invoke-RestMethod -Uri "$($SecuritySettings.sonar.hostUrl)/api/ce/task?id=$([Uri]::EscapeDataString($Properties.ceTaskId))" -Headers $Headers -TimeoutSec 30
        if ($Task.task.status -ne 'SUCCESS' -or -not $Task.task.analysisId) { throw 'Sonar task has not produced a successful analysis.' }
        $Gate = Invoke-RestMethod -Uri "$($SecuritySettings.sonar.hostUrl)/api/qualitygates/project_status?analysisId=$([Uri]::EscapeDataString($Task.task.analysisId))" -Headers $Headers -TimeoutSec 30
        $Gate | ConvertTo-Json -Depth 20 | Set-Content "$Directory/sonar-gate.json"
        if ($Gate.projectStatus.status -ne 'OK') { Register-SecurityViolation "Sonar quality gate is not OK for $Component." }
    }
}
finally { Pop-Location }
