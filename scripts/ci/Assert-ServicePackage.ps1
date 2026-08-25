[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet(
        "auth-service",
        "patient-service",
        "appointment-service",
        "clinical-service",
        "notification-service"
    )]
    [string]$Service
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$TargetDirectory = Join-Path $RepositoryRoot "$Service/target"

$Jars = @(
    Get-ChildItem -LiteralPath $TargetDirectory -File -Filter "$Service-*.jar" |
        Where-Object { $_.Name -notmatch '\.original$' }
)
if ($Jars.Count -ne 1) {
    throw "Expected exactly one executable $Service JAR, found $($Jars.Count)."
}

$RequiredEvidence = @(
    (Join-Path $TargetDirectory "site/jacoco/index.html"),
    (Join-Path $TargetDirectory "site/jacoco/jacoco.xml"),
    (Join-Path $TargetDirectory "pit-reports/index.html"),
    (Join-Path $TargetDirectory "pit-reports/mutations.xml")
)
foreach ($Path in $RequiredEvidence) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required ${Service} package evidence is missing: $Path"
    }
}

Push-Location $RepositoryRoot
try {
    $JarListing = & jar tf $Jars[0].FullName
    $HasBootInfEntry = $JarListing | Where-Object { $_ -like "BOOT-INF/*" }
    if ($LASTEXITCODE -ne 0 -or -not $HasBootInfEntry) {
        throw "$($Jars[0].Name) is not an executable Spring Boot JAR."
    }
}
finally { Pop-Location }

Write-Host "Validated package evidence for ${Service}: $($Jars[0].FullName)"
