#Requires -Version 7
<#
.SYNOPSIS
Runs the throwaway Keycloak identity spike for issue #85 (ADR-027) and writes evidence to out/.

.PARAMETER Mode
optimized: pre-built image, `start --optimized` (the shape #86 should ship).
auto-build: upstream image, plain `start`, which re-augments Quarkus on every boot.

.PARAMETER MemoryLimit
Container memory limit for Keycloak, for example 1g or 768m.

.PARAMETER Keep
Leave the stack running afterwards (for the admin console at http://localhost:8180/admin).
#>
param(
    [ValidateSet('optimized', 'auto-build')][string]$Mode = 'optimized',
    [string]$MemoryLimit = '1g',
    [switch]$Keep
)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
New-Item -ItemType Directory -Force out | Out-Null

function New-Secret { [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24)).ToLowerInvariant() }

if (-not (Test-Path .env)) {
    # Generated, Git-ignored, spike-only values; the realm policy needs 12+ characters.
    $generated = 'KC_DB_PASSWORD', 'KC_BOOTSTRAP_ADMIN_PASSWORD', 'CLINICFLOW_BFF_CLIENT_SECRET', 'SPIKE_DEMO_PASSWORD'
    $generated | ForEach-Object { '{0}={1}' -f $_, (New-Secret) } | Set-Content .env
}
Get-Content .env | ForEach-Object {
    $name, $value = $_ -split '=', 2
    Set-Item "env:$name" $value
}
$env:KC_MEM_LIMIT = $MemoryLimit
if ($Mode -eq 'auto-build') {
    $env:KC_IMAGE = 'quay.io/keycloak/keycloak:26.8.1@sha256:79c623a22abacf3fe6c00a5fb30f5b7843d717b234033b80995891c06116d244'
    $env:KC_COMMAND = 'start --import-realm'
} else {
    Remove-Item env:KC_IMAGE, env:KC_COMMAND -ErrorAction SilentlyContinue
    docker compose build keycloak | Out-Null
}

function Wait-Healthy([string]$label) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    docker compose up -d --wait keycloak | Out-Null
    $started = (docker compose logs keycloak | Select-String 'started in ([0-9.]+)s' | Select-Object -Last 1).Matches.Groups[1].Value
    "{0}: ready (HTTP 200 on /health/ready) after {1:n1}s wall clock; Quarkus reports started in {2}s" -f $label, $watch.Elapsed.TotalSeconds, $started
}
function Get-Footprint([string]$label) {
    $stats = docker stats --no-stream --format '{{.Name}}|{{.MemUsage}}|{{.CPUPerc}}' |
        Where-Object { $_ -like 'clinicflow-keycloak-spike-*' }
    $stats | ForEach-Object { "{0}: {1}" -f $label, $_ }
}

$summary = [Collections.Generic.List[string]]::new()
docker compose down -v --remove-orphans 2>$null | Out-Null
$summary.Add("mode: $Mode, memory limit: $MemoryLimit")
$summary.Add((Wait-Healthy 'cold start with realm import'))
$summary.AddRange([string[]](Get-Footprint 'idle after cold start'))
docker compose restart keycloak | Out-Null
$summary.Add((Wait-Healthy 'warm restart'))
$summary.Add(((docker compose logs keycloak | Select-String "Realm 'clinicflow' already exists|Realm 'clinicflow' imported" | Select-Object -Last 1).Line -replace '^.*?\| ', ''))

$users = @(
    'admin@demo.clinicflow.local'
    'sofia.almeida@demo.clinicflow.local'
    'ana.martins@demo.clinicflow.local'
)
foreach ($email in $users) {
    $slug = $email.Split('@')[0]
    py -I probe.py login --email $email --grant "out/grant-$slug.json"
    if ($LASTEXITCODE) { throw "browser leg failed for $email" }
    docker compose --profile probe run --rm probe python -I probe.py bff --grant "out/grant-$slug.json" --out "out/token-$slug.json" | Out-Null
    if ($LASTEXITCODE) { throw "BFF leg failed for $email" }
}
py -I probe.py register --email 'self.registered@spike.local' --grant out/grant-registered.json
if ($LASTEXITCODE) { throw 'registration leg failed' }
docker compose --profile probe run --rm probe python -I probe.py bff --grant out/grant-registered.json --out out/token-registered.json | Out-Null
py -I probe.py admin --out out/admin.json | Out-Null
$summary.AddRange([string[]](Get-Footprint 'after logins'))

$summary | Set-Content "out/summary-$Mode-$MemoryLimit.txt"
$summary
if (-not $Keep) { docker compose down -v --remove-orphans 2>$null | Out-Null }
