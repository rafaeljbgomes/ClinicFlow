# Frees Docker Desktop's resources without deleting anything: every Deployment and StatefulSet in the
# application and monitoring namespaces, Keycloak and the databases included, goes to zero replicas.
# Persistent volumes, Secrets and Helm releases stay; scripts/deploy-local-kubernetes.ps1 restores them.
[CmdletBinding()]
param(
    [string]$Namespace = "clinicflow",
    [string]$MonitoringNamespace = "monitoring"
)

$ErrorActionPreference = "Stop"

function Assert-Command {
    param([Parameter(Mandatory)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' was not found in PATH."
    }
}

function Invoke-Checked {
    # A simple function keeps native flags such as kubectl's -o out of
    # PowerShell's common-parameter binding (OutVariable/OutBuffer).
    param([string]$Command)
    $Arguments = $args

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $Command $($Arguments -join ' ')"
    }
}

Assert-Command kubectl

Write-Host "Scaling Deployments in namespace '$Namespace' to zero replicas..."
Invoke-Checked kubectl scale deployment --all --replicas=0 --namespace $Namespace

Write-Host "Scaling StatefulSets in namespace '$Namespace' to zero replicas..."
Invoke-Checked kubectl scale statefulset --all --replicas=0 --namespace $Namespace

Write-Host "Scaling Deployments in namespace '$MonitoringNamespace' to zero replicas..."
Invoke-Checked kubectl scale deployment --all --replicas=0 --namespace $MonitoringNamespace

Write-Host "Scaling StatefulSets in namespace '$MonitoringNamespace' to zero replicas..."
Invoke-Checked kubectl scale statefulset --all --replicas=0 --namespace $MonitoringNamespace

Write-Host ""
Write-Host "Remaining ClinicFlow workloads:"
Invoke-Checked kubectl get pods --all-namespaces -o wide
