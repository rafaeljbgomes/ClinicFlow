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
    [string]$Service,

    [Parameter(Mandatory)]
    [ValidateSet("Build", "Unit", "Integration", "Mutation")]
    [string]$Phase
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path

$ArgumentsByPhase = @{
    Build = @("-B", "-ntp", "-pl", ":$Service", "-am", "clean", "compile")
    Unit = @("-B", "-ntp", "-pl", ":$Service", "-am", "test")
    Integration = @("-B", "-ntp", "-pl", ":$Service", "-am", "verify", "-Dskip.unit.tests=true")
    Mutation = @(
        "-B",
        "-ntp",
        "-pl",
        ":$Service",
        "-am",
        "-Pmutation",
        "test-compile",
        "org.pitest:pitest-maven:mutationCoverage"
    )
}

Push-Location $RepositoryRoot
try {
    $MavenArguments = $ArgumentsByPhase[$Phase]
    & ./mvnw @MavenArguments
    if ($LASTEXITCODE -ne 0) { throw "$Service $Phase phase failed." }
}
finally { Pop-Location }
