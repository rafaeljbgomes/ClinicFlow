[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet("Unit", "Integration")]
    [string]$Phase
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$ArgumentsByPhase = @{
    Unit = @("-B", "-ntp", "clean", "test")
    Integration = @("-B", "-ntp", "verify", "-Dskip.unit.tests=true")
}

Push-Location $RepositoryRoot
try {
    $MavenArguments = $ArgumentsByPhase[$Phase]
    & ./mvnw @MavenArguments
    if ($LASTEXITCODE -ne 0) { throw "System backend $Phase phase failed." }
}
finally { Pop-Location }
