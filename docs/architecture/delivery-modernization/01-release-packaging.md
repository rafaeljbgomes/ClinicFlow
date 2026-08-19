# DM-01: Separate Service Release Packaging

GitHub tracking issue: [#1](https://github.com/rafaeljbgomes/ClinicFlow/issues/1)

Implementation status: completed in issue #1. The follow-up migration from
Helm 3 to Helm 4 is tracked in
[issue #2](https://github.com/rafaeljbgomes/ClinicFlow/issues/2).

## Problem

The current `clinicflow` Helm release renders every backend service and the
frontend. A single `global.imageTag` selects the image version for all of them.
The local Kubernetes script also builds every image before upgrading the
release. Kubernetes workloads are separate, but their release and rollback
lifecycle is coupled.

## Outcome

Each application component has a separately installable and rollbackable Helm
release with its own image repository and immutable image reference. The
platform remains separately installable.

Expected release names:

- `clinicflow-auth`
- `clinicflow-patient`
- `clinicflow-appointment`
- `clinicflow-clinical`
- `clinicflow-notification`
- `clinicflow-frontend`
- `clinicflow-platform`

## Scope

- Define one reusable Spring-service application chart, installed five times
  with service-specific values, for a Deployment, Service, probes, resources,
  configuration references, ServiceMonitor, and service-level NetworkPolicy.
- Keep the Next.js frontend in its own application chart because its ports,
  probes, configuration, and dependency access differ from Spring services.
- Move PostgreSQL, RabbitMQ, shared configuration, and shared dashboard assets
  to a platform chart or clearly defined platform release boundary.
- Replace the application-wide image tag with a per-service immutable image
  reference.
- Preserve a one-command local full-stack installation through the existing
  PowerShell orchestration script. Do not introduce an umbrella release because
  it would recreate the coupled upgrade and rollback boundary.
- Document install, upgrade, status, and rollback commands for one service.

## Out of Scope

- Registry publishing, environment promotion, and remote deployment credentials
  (DM-03).
- Jenkins job creation (DM-02).
- Production database hosting or secret-manager selection (DM-04).
- Domain-service changes, event redesign, or a change to the Docker Compose
  developer workflow.

## Touchpoints

- `deploy/helm/platform/**`
- `deploy/helm/service/**`, `deploy/helm/services/**`, and
  `deploy/helm/frontend/**`
- `scripts/deploy-local-kubernetes.ps1`
- `docs/development/local-development.md`
- `docs/infrastructure/kubernetes-observability.md`

## Resolved Decisions

- Reuse one installable Spring-service chart with five values files rather than
  duplicating six thin charts or adding library-chart wrappers.
- Use a dedicated frontend application chart.
- Keep local full-stack orchestration in PowerShell, with an optional
  single-component selector.
- Give each application release its own ConfigMap and make it reference stable
  platform/operator-owned Secret and service names.
- Keep PostgreSQL, RabbitMQ, namespace-wide policy, RabbitMQ monitoring, and
  shared Grafana dashboards in `clinicflow-platform`.

## Acceptance Criteria

- Changing the patient image reference changes only the patient release and
  patient Deployment revision.
- `helm rollback clinicflow-patient <revision>` does not alter any other
  application Deployment.
- Every service has its own image reference; no application-wide `imageTag`
  controls all service images.
- The local full-stack workflow still starts all services, PostgreSQL, RabbitMQ,
  and observability.
- Rendered manifests pass `helm lint`, `helm template`, and `kubeconform`.

## Rollback Boundary

The migration must coexist with the current chart until a fresh local cluster
can install the new releases and a complete stack can be removed without
orphaned shared resources. Do not let two releases own the same Deployment,
Service, PVC, Secret, or ConfigMap name.
