#Requires -Version 7
<#
.SYNOPSIS
Re-applies docker/keycloak/realm/clinicflow-realm.json to the local Compose Keycloak (ADR-027).

.DESCRIPTION
Keycloak imports the realm only when it does not exist yet, so later edits to the realm file, and a
client secret rotated by generate-dev-credentials.ps1 -Rotate, need a reset. The default reset
rebuilds the image and replaces the clinicflow realm in place: users who registered themselves,
sessions and events are removed, and the demo users come back with their fixed ids.

.PARAMETER Full
Drops Keycloak's whole database instead, including the master realm, so the next start also applies
the bootstrap administrator from secrets/keycloak/admin.env.
#>
[CmdletBinding()]
param([switch]$Full)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Invoke-Compose {
    & docker compose @args
    if ($LASTEXITCODE -ne 0) { throw "docker compose $($args -join ' ') failed." }
}

Push-Location $repoRoot
try {
    Invoke-Compose build keycloak
    Invoke-Compose rm --stop --force keycloak
    if ($Full) {
        # The administrator connects over the container's local socket, which the image trusts.
        Invoke-Compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -q -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE IF EXISTS clinicflow_keycloak WITH (FORCE)"'
        Invoke-Compose run --rm --no-deps postgres-bootstrap
    }
    else {
        Invoke-Compose run --rm --no-deps keycloak import --optimized --dir /opt/keycloak/data/import --override true
    }
    Invoke-Compose up --detach --no-deps --wait keycloak
    Write-Host "The clinicflow realm matches docker/keycloak/realm/clinicflow-realm.json."
}
finally { Pop-Location }
