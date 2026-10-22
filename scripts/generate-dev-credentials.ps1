[CmdletBinding()]
param(
    # Replace every existing password; the next bootstrap run and restart apply them (ADR-026).
    [switch]$Rotate
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$identitiesFile = Join-Path $repoRoot "deploy/helm/platform/files/bootstrap/service-identities.conf"
$platformDir = Join-Path $repoRoot "secrets/platform"
$servicesDir = Join-Path $repoRoot "secrets/services"

function New-Password {
    $alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    -join (1..32 | ForEach-Object { $alphabet[[Security.Cryptography.RandomNumberGenerator]::GetInt32($alphabet.Length)] })
}

function Write-EnvFile {
    param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][System.Collections.Specialized.OrderedDictionary]$Values)

    if ((Test-Path $Path) -and -not $Rotate) {
        Write-Host "  kept      $Path"
        return
    }
    $content = ($Values.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join "`n"
    # LF and no BOM: the bootstrap scripts source these files with sh.
    [IO.File]::WriteAllText($Path, "$content`n", [Text.UTF8Encoding]::new($false))
    if (-not $IsWindows) { & chmod 600 $Path }
    Write-Host "  generated $Path"
}

foreach ($directory in @($platformDir, $servicesDir)) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    if (-not $IsWindows) { & chmod 700 $directory }
}

Write-Host "Platform administrator credentials:"
Write-EnvFile (Join-Path $platformDir "postgres.env") ([ordered]@{ POSTGRES_USER = "clinicflow"; POSTGRES_PASSWORD = (New-Password) })
Write-EnvFile (Join-Path $platformDir "rabbitmq.env") ([ordered]@{ RABBITMQ_DEFAULT_USER = "clinicflow"; RABBITMQ_DEFAULT_PASS = (New-Password) })

Write-Host "Service credentials:"
foreach ($line in Get-Content $identitiesFile) {
    if ($line -match '^\s*(#|$)') { continue }
    $fields = -split $line
    $service = $fields[0]
    $short = $service -replace '-service$', ''
    $values = [ordered]@{
        SPRING_DATASOURCE_USERNAME = "clinicflow_${short}_app"
        SPRING_DATASOURCE_PASSWORD = (New-Password)
        SPRING_FLYWAY_USER = "clinicflow_${short}_owner"
        SPRING_FLYWAY_PASSWORD = (New-Password)
    }
    # The third column is the RabbitMQ configure pattern; "-" means no broker access.
    if ($fields[2] -ne "-") {
        $values.SPRING_RABBITMQ_USERNAME = $service
        $values.SPRING_RABBITMQ_PASSWORD = (New-Password)
    }
    Write-EnvFile (Join-Path $servicesDir "$service.env") $values
}

Write-Host "These credentials are for local development only and are ignored by Git."
