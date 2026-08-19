# Kubernetes and Observability

This phase moves ClinicFlow from Docker Compose into a local Kubernetes
environment and adds Prometheus/Grafana observability. The goal is a serious
learning and portfolio setup, not a production clinical deployment.

## Deployment Model

The Kubernetes target is Docker Desktop Kubernetes. Shared capabilities and
application components have separate Helm releases:

The supported Helm CLI line is 4.x. Jenkins pins the validated patch in
`deploy/jenkins/agent/Dockerfile`, while the local installer accepts Helm 4
patch updates. The migration explicitly uses client-side apply until
server-side apply is evaluated independently. Progress and validation evidence
are tracked in [GitHub issue #2](https://github.com/rafaeljbgomes/ClinicFlow/issues/2).
Application and platform releases use Helm 4's watcher strategy. Observability
uses the legacy strategy because the bundled kube-prometheus-stack admission
hooks declare `before-hook-creation`; the watcher waits for nonexistent hook
resources until the timeout before continuing a fresh installation.

- `clinicflow-observability`, installed in `monitoring`, wraps
  `kube-prometheus-stack` version `88.3.0`.
- `clinicflow-platform`, installed in `clinicflow`, owns local PostgreSQL,
  RabbitMQ, their credentials, namespace-wide policy, RabbitMQ monitoring, and
  shared Grafana dashboards.
- `clinicflow-auth`, `clinicflow-patient`, `clinicflow-appointment`,
  `clinicflow-clinical`, and `clinicflow-notification` each install the reusable
  Spring-service chart with service-specific values.
- `clinicflow-frontend` owns the Next.js frontend and BFF resources.

Application releases use their own immutable image tag or digest. The local
script derives a commit/timestamp tag by default. Docker Desktop Kubernetes can
use those images directly because it shares the local Docker image store.

## Automated Deployment

From the repository root, deploy the complete local stack with:

```powershell
.\scripts\deploy-local-kubernetes.ps1
```

Use `-Component` to build and upgrade only one application. Reusing an existing
image with `-SkipBuild` requires its explicit immutable tag:

```powershell
.\scripts\deploy-local-kubernetes.ps1 -Component Patient
.\scripts\deploy-local-kubernetes.ps1 -Component Patient `
  -SkipBuild -ImageTag git-<commit>-<timestamp>
```

The remaining sections show the equivalent individual commands for validation
and troubleshooting.

## Kubernetes Concepts Used

- `Deployment`: runs each stateless service: frontend, auth, patient,
  appointment, and notification.
- `StatefulSet`: runs local PostgreSQL and RabbitMQ with stable identities and
  persistent volume claims.
- `Service`: provides stable in-cluster DNS names such as `auth-service`,
  `postgres`, and `rabbitmq`.
- `ConfigMap`: stores non-secret runtime configuration and Grafana dashboard
  JSON.
- `Secret`: stores JWT key material and local infrastructure credentials.
- `Probe`: Spring services use `/actuator/health/liveness` and
  `/actuator/health/readiness` on management port `9000`.
- `NetworkPolicy`: declares default-deny traffic and then allows only the
  frontend, backend, database, RabbitMQ, DNS, and monitoring flows required by
  the prototype.
- `ServiceMonitor`: tells Prometheus Operator how to scrape Spring Actuator and
  RabbitMQ metrics.

## Commands

Generate local JWT keys if needed:

```powershell
.\scripts\generate-dev-jwt-keys.ps1
```

Build application images:

```powershell
docker build -t clinicflow/auth-service:0.1.0-local -f auth-service/Dockerfile .
docker build -t clinicflow/patient-service:0.1.0-local -f patient-service/Dockerfile .
docker build -t clinicflow/appointment-service:0.1.0-local -f appointment-service/Dockerfile .
docker build -t clinicflow/clinical-service:0.1.0-local -f clinical-service/Dockerfile .
docker build -t clinicflow/notification-service:0.1.0-local -f notification-service/Dockerfile .
docker build -t clinicflow/frontend:0.1.0-local -f frontend/Dockerfile frontend
```

Create the application namespace and JWT Secret:

```powershell
kubectl create namespace clinicflow
kubectl create secret generic clinicflow-jwt `
  --from-file=jwt-private.pem=secrets/jwt-private.pem `
  --from-file=jwt-public.pem=secrets/jwt-public.pem `
  -n clinicflow
```

Install observability:

```powershell
helm dependency update deploy/helm/observability
helm upgrade --install clinicflow-observability deploy/helm/observability `
  -n monitoring --create-namespace `
  -f deploy/helm/observability/values-docker-desktop.yaml `
  --rollback-on-failure --wait=legacy --server-side=false --timeout 5m
```

Validate and install the independently managed releases (replace the example
tag with an image that exists locally):

```powershell
helm lint deploy/helm/platform -f deploy/helm/platform/values-docker-desktop.yaml
helm upgrade --install clinicflow-platform deploy/helm/platform `
  -n clinicflow -f deploy/helm/platform/values-docker-desktop.yaml `
  --rollback-on-failure --wait=watcher --server-side=false --timeout 5m

$imageTag = "git-<commit>-<timestamp>"
foreach ($service in "auth", "patient", "appointment", "clinical", "notification") {
  helm lint deploy/helm/service -f "deploy/helm/services/$service.yaml"
  helm upgrade --install "clinicflow-$service" deploy/helm/service `
    -n clinicflow -f "deploy/helm/services/$service.yaml" `
    --set-string "image.tag=$imageTag" `
    --rollback-on-failure --wait=watcher --server-side=false --timeout 5m
}

helm lint deploy/helm/frontend
helm upgrade --install clinicflow-frontend deploy/helm/frontend `
  -n clinicflow --set-string "image.tag=$imageTag" `
  --rollback-on-failure --wait=watcher --server-side=false --timeout 5m
```

Inspect and roll back only patient-service:

```powershell
helm status clinicflow-patient -n clinicflow
helm history clinicflow-patient -n clinicflow
helm rollback clinicflow-patient <revision> -n clinicflow `
  --wait=watcher --server-side=false --timeout 5m
```

For a complete removal, uninstall applications before the shared platform:

```powershell
helm uninstall clinicflow-frontend clinicflow-notification clinicflow-clinical `
  clinicflow-appointment clinicflow-patient clinicflow-auth -n clinicflow
helm uninstall clinicflow-platform -n clinicflow
helm uninstall clinicflow-observability -n monitoring
```

Validate rollouts:

```powershell
kubectl get pods -n clinicflow
kubectl rollout status statefulset/postgres -n clinicflow
kubectl rollout status statefulset/rabbitmq -n clinicflow
kubectl rollout status deployment/auth-service -n clinicflow
kubectl rollout status deployment/patient-service -n clinicflow
kubectl rollout status deployment/appointment-service -n clinicflow
kubectl rollout status deployment/clinical-service -n clinicflow
kubectl rollout status deployment/notification-service -n clinicflow
kubectl rollout status deployment/frontend -n clinicflow
```

Port-forward the UI and Grafana:

```powershell
kubectl port-forward svc/frontend 3000:3000 -n clinicflow
kubectl port-forward svc/clinicflow-observability-grafana 3001:80 -n monitoring
```

## Metrics and Dashboards

Spring services expose metrics on `/actuator/prometheus` only when the
`kubernetes` profile is active. The application API ports remain `8081` through
`8085`; management traffic uses port `9000`.

Expected Prometheus queries:

```promql
up{namespace="clinicflow"}
sum by (application, status) (rate(http_server_requests_seconds_count{namespace="clinicflow"}[5m]))
sum by (service, event_type, status) (rate(clinicflow_domain_events_published_total{namespace="clinicflow"}[5m]))
sum by (service, event_type, status) (rate(clinicflow_domain_events_consumed_total{namespace="clinicflow"}[5m]))
rabbitmq_queue_messages_ready{namespace="clinicflow"}
```

Grafana loads the `ClinicFlow Platform` dashboard from the platform chart
through the dashboard sidecar configured in kube-prometheus-stack.

## Troubleshooting

- If backend pods fail to start with missing JWT files, recreate the
  `clinicflow-jwt` Secret in the `clinicflow` namespace.
- If image pulls fail, rebuild the local images and confirm Docker Desktop
  Kubernetes is the active cluster.
- If Prometheus targets are missing, confirm the observability release is
  installed before the application release and that the ServiceMonitor CRDs are
  present.
- If Grafana does not show the dashboard, restart the Grafana pod or confirm the
  ConfigMap has label `grafana_dashboard=1`.
- If NetworkPolicies block traffic in a CNI-enabled local cluster, inspect the
  frontend and service charts plus PostgreSQL, RabbitMQ, and default-deny
  policies in `deploy/helm/platform/templates/networkpolicy.yaml`.

## Future Production Direction

For a non-local deployment, keep the service boundaries and observability
contract, but replace local persistence and local secrets:

- use managed PostgreSQL or a PostgreSQL operator instead of the local
  StatefulSet;
- use managed RabbitMQ or a RabbitMQ operator instead of the local StatefulSet;
- use External Secrets, Sealed Secrets, or a cloud secret manager for JWT and
  infrastructure credentials;
- add Ingress/TLS, HSTS, image scanning, SBOM generation, and registry-based
  image promotion in CI/CD.
