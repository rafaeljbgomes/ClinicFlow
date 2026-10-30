#Requires -Version 7
<#
.SYNOPSIS
Re-applies docker/keycloak/realm/clinicflow-realm.json to the local Keycloak (ADR-027).

.DESCRIPTION
Keycloak imports the realm only when it does not exist yet, so later edits to the realm file, and a
client secret rotated by generate-dev-credentials.ps1 -Rotate, need a reset. The default reset
rebuilds the image and replaces the clinicflow realm in place: users who registered themselves,
sessions and events are removed, and the demo users come back with their fixed ids.

.PARAMETER Full
Drops Keycloak's whole database instead, including the master realm, so the next start also applies
the bootstrap administrator from secrets/keycloak/admin.env.

.PARAMETER Target
Compose (the default) or Kubernetes, the clinicflow-keycloak release that
scripts/deploy-local-kubernetes.ps1 installs. On Kubernetes the import runs in a one-off pod with
Keycloak's own configuration, labels and network policy while the StatefulSet is scaled to zero.
#>
[CmdletBinding()]
param(
    [switch]$Full,
    [ValidateSet("Compose", "Kubernetes")][string]$Target = "Compose",
    [string]$Namespace = "clinicflow",
    [string]$Timeout = "5m"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Invoke-Compose {
    & docker compose @args
    if ($LASTEXITCODE -ne 0) { throw "docker compose $($args -join ' ') failed." }
}

function Invoke-Checked {
    # A simple function keeps native flags such as kubectl's -o out of
    # PowerShell's common-parameter binding (OutVariable/OutBuffer).
    param([string]$Command)
    $Arguments = $args

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed with exit code ${LASTEXITCODE}: $Command $($Arguments -join ' ')" }
}

function Reset-ComposeRealm {
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
}

# Runs Keycloak's import once, from the StatefulSet's own pod template, so it gets the same image,
# environment, Secrets, security context and NetworkPolicy as the server it replaces.
function Invoke-KubernetesImport([string]$Image) {
    $statefulSet = (& kubectl get statefulset keycloak --namespace $Namespace -o json) | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0) { throw "StatefulSet 'keycloak' was not found; deploy -Component Keycloak first." }
    $spec = $statefulSet.spec.template.spec
    $container = $spec.containers[0]
    $container.image = $Image
    $container | Add-Member -NotePropertyName args -NotePropertyValue @("import", "--optimized", "--dir", "/opt/keycloak/data/import", "--override", "true") -Force
    foreach ($probe in @("livenessProbe", "readinessProbe", "startupProbe")) { $container.PSObject.Properties.Remove($probe) }
    $spec.restartPolicy = "Never"
    $name = "keycloak-realm-reset-$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"
    $pod = [ordered]@{
        apiVersion = "v1"
        kind = "Pod"
        metadata = [ordered]@{ name = $name; namespace = $Namespace; labels = $statefulSet.spec.template.metadata.labels }
        spec = $spec
    }
    try {
        $pod | ConvertTo-Json -Depth 32 | & kubectl create -f - | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Failed to create the realm import pod." }
        $deadline = [DateTimeOffset]::UtcNow.AddMinutes(5)
        do {
            Start-Sleep -Seconds 3
            $phase = & kubectl get pod $name --namespace $Namespace -o 'jsonpath={.status.phase}'
        } until ($phase -in @("Succeeded", "Failed") -or [DateTimeOffset]::UtcNow -gt $deadline)
        if ($phase -ne "Succeeded") {
            & kubectl logs $name --namespace $Namespace --tail 40
            throw "The realm import pod ended in phase '$phase'."
        }
    }
    finally { & kubectl delete pod $name --namespace $Namespace --ignore-not-found --wait=false | Out-Null }
}

function Reset-KubernetesRealm {
    & helm status clinicflow-keycloak --namespace $Namespace *> $null
    if ($LASTEXITCODE -ne 0) { throw "Release 'clinicflow-keycloak' is not installed; deploy -Component Keycloak first." }

    # The realm is built into the image, so the import uses a fresh build, tagged as the deploy script does.
    Invoke-Checked docker build --provenance=false -t clinicflow/keycloak:local docker/keycloak
    $imageId = (& docker image inspect clinicflow/keycloak:local --format '{{.Id}}')
    if ($LASTEXITCODE -ne 0 -or "$imageId" -notmatch '^sha256:[0-9a-f]{64}$') { throw "Cannot read the Keycloak image ID." }
    $imageTag = "id-$("$imageId".Substring(7, 12))"
    Invoke-Checked docker tag clinicflow/keycloak:local "clinicflow/keycloak:$imageTag"

    Write-Host "Stopping Keycloak..."
    Invoke-Checked kubectl scale statefulset keycloak --replicas=0 --namespace $Namespace
    Invoke-Checked kubectl wait --for=delete pod/keycloak-0 --namespace $Namespace --timeout=120s

    if ($Full) {
        Write-Host "Dropping Keycloak's database and re-running the platform bootstrap..."
        # The administrator connects over the pod's local socket, which the image trusts. Called directly:
        # through Invoke-Checked, sh's -c would bind its -Command parameter and the '--' would be dropped.
        & kubectl exec postgres-0 --namespace $Namespace -- sh -c 'psql -v ON_ERROR_STOP=1 -q -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE IF EXISTS clinicflow_keycloak WITH (FORCE)"'
        if ($LASTEXITCODE -ne 0) { throw "Dropping Keycloak's database failed." }
        Invoke-Checked helm upgrade clinicflow-platform (Join-Path $repoRoot "deploy/helm/platform") --namespace $Namespace --reuse-values --rollback-on-failure --wait=watcher --server-side=false --timeout $Timeout
    }
    else {
        Write-Host "Importing the clinicflow realm..."
        Invoke-KubernetesImport "clinicflow/keycloak:$imageTag"
    }

    # Records the realm Keycloak now holds and starts it again with the image that carries it.
    $realmChecksum = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $repoRoot "docker/keycloak/realm/clinicflow-realm.json")).Hash.ToLowerInvariant()
    Invoke-Checked helm upgrade clinicflow-keycloak (Join-Path $repoRoot "deploy/helm/keycloak") --namespace $Namespace --reuse-values `
        --set-string "keycloakx.image.tag=$imageTag" `
        --set-string "global.clinicflow.checksums.realm=$realmChecksum" `
        --rollback-on-failure --wait=watcher --server-side=false --timeout $Timeout
    Invoke-Checked kubectl scale statefulset keycloak --replicas=1 --namespace $Namespace
    Invoke-Checked kubectl rollout status statefulset/keycloak --namespace $Namespace --timeout=$Timeout
}

Push-Location $repoRoot
try {
    if ($Target -eq "Kubernetes") { Reset-KubernetesRealm } else { Reset-ComposeRealm }
    Write-Host "The clinicflow realm matches docker/keycloak/realm/clinicflow-realm.json."
}
finally { Pop-Location }
