[CmdletBinding()]
param([switch]$Upgrade)
$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$AdmissionApi = & kubectl api-resources --api-group=admissionregistration.k8s.io -o name
if ($LASTEXITCODE -ne 0 -or $AdmissionApi -notcontains 'validatingadmissionpolicies.admissionregistration.k8s.io') { throw 'The pinned standard bundle requires ValidatingAdmissionPolicy support.' }
$Provenance = Get-Content (Join-Path $Root 'deploy/gateway/provenance.json') -Raw | ConvertFrom-Json
foreach ($Artifact in $Provenance.artifacts) {
    $Path = Join-Path $Root $Artifact.path
    if ((Get-FileHash $Path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $Artifact.sha256) { throw "Artifact checksum mismatch: $Path" }
}
foreach ($Definition in @(@{File='gateway-api-1.6.2.yaml';Version='1.6.2'},@{File='traefik-41.6.1.yaml';Version='41.6.1'})) {
    $Path = Join-Path $Root "deploy/gateway/crds/$($Definition.File)"
    $Names = @((Get-Content $Path -Raw) -split '(?m)^---\s*$' | Where-Object { $_ -match '(?m)^kind: CustomResourceDefinition$' } | ForEach-Object { [regex]::Match($_,'(?m)^  name: (.+)$').Groups[1].Value })
    foreach ($Name in $Names) {
        $Existing = & kubectl get crd $Name -o json --ignore-not-found
        if ($LASTEXITCODE -ne 0) { throw "Cannot inspect CRD $Name" }
        if ($Existing) {
            $Object = $Existing | ConvertFrom-Json
            $Installed = $Object.metadata.annotations.'clinicflow.io/crd-version'
            if ($Installed -ne $Definition.Version -and -not $Upgrade) {
                throw "CRD $Name has version '$Installed'; use -Upgrade only after reviewing CRD compatibility and rollback limits."
            }
        }
    }
    # Explicit platform operation: CRDs exceed the client-side annotation limit.
    & kubectl apply --server-side --field-manager=clinicflow-gateway-crds -f $Path
    if ($LASTEXITCODE -ne 0) { throw "CRD bootstrap failed: $Path" }
    foreach ($Name in $Names) {
        & kubectl annotate crd $Name "clinicflow.io/crd-version=$($Definition.Version)" --overwrite
        if ($LASTEXITCODE -ne 0) { throw "CRD version annotation failed: $Name" }
        & kubectl wait --for=condition=Established "crd/$Name" --timeout=120s
        if ($LASTEXITCODE -ne 0) { throw "CRD was not established: $Name" }
    }
}
