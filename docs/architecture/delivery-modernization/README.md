# Delivery Modernization Work Packages

## Purpose

This directory splits ClinicFlow's deployment modernization into isolated,
reviewable changes. It implements ADR-017, not a simultaneous rewrite of CI,
CD, Helm, Kubernetes, and local development.

The intended end state is independent delivery of `auth-service`,
`patient-service`, `appointment-service`, `clinical-service`,
`notification-service`, and `frontend`. It does **not** require an independent
Jenkins controller, Kubernetes cluster, broker, observability stack, or
physical server for each service. Those are shared platform capabilities with
explicit access and ownership boundaries.

## Baseline and DM-01 Evidence

- Each application already has a separate Dockerfile, Kubernetes Deployment,
  and Kubernetes Service.
- Jenkins is currently local CI only: it publishes no image and deploys no
  environment.
- DM-02 replaces the root pipeline with six independently executable component
  pipelines and `Jenkinsfile.system` for platform, Compose, and Playwright
  evidence. The retired legacy Jenkins job remains disabled as a rollback
  boundary.
- Before DM-01, `deploy/helm/clinicflow` rendered every workload with one
  `global.imageTag`. Issue #1 replaces it with platform, reusable service, and
  frontend charts plus per-service values and orchestration.
- DM-01 validation proved seven independent releases, a patient-only upgrade
  and rollback, and ordered uninstall without remaining Helm-owned resources.
- Each service has its own Flyway-managed database, but all currently use the
  same PostgreSQL role and shared secret in Kubernetes.

This remains a local Docker Desktop Kubernetes capability. It does not claim a
registry, remote Jenkins instance, or production environment.

## Work Package Catalogue

| ID | Work package | Outcome | Depends on |
| --- | --- | --- | --- |
| DM-01 | [Release packaging](01-release-packaging.md) ([issue #1](https://github.com/rafaeljbgomes/ClinicFlow/issues/1)) | One Helm release and image reference per service | ADR-017 |
| DM-02 | [Service CI](02-service-ci.md) ([issue #6](https://github.com/rafaeljbgomes/ClinicFlow/issues/6)) | Independent component pipelines plus focused system CI | DM-01 interface agreed |
| DM-03 | [Artifact promotion and CD](03-artifact-promotion-and-cd.md) | Immutable images and per-service promotion, verification, and rollback | DM-01, DM-02, registry/environment decisions |
| DM-04 | [Platform access boundaries](04-platform-access-boundaries.md) | Per-service database credentials and clearly separated application/platform responsibility | DM-01 target manifest boundary |
| DM-05 | [System validation](05-system-validation.md) | A focused integration gate retained alongside service pipelines | DM-01 and DM-02 |

DM-01 established the release boundary used by DM-02. DM-03 still requires
explicit choices about registry and deployment environments, so it must not be
implemented by assumption.

## Shared Guardrails

- Preserve the monorepo and its Maven parent; a repository boundary is not the
  same as a deployment boundary.
- Keep `event-contracts` treated as a compatibility-sensitive shared contract;
  changes to it require provider/consumer and system validation.
- Use immutable image references (digest preferred; commit-derived tag at
  minimum). Never promote a mutable `latest` tag.
- Use expand/contract database migrations and backward-compatible HTTP/event
  contracts during rolling deployments.
- Keep Docker Compose as the full-stack local-development workflow.
- Retain the current root pipeline as a system-quality gate until its
  replacement has demonstrated equivalent coverage.
- Make every work package separately reversible and verify that deploying or
  rolling back one service does not alter another service's revision.

## Planning Rule

Each work package becomes its own GitHub issue or implementation task only
after its open decisions are resolved. The authoritative remote repository is
[rafaeljbgomes/ClinicFlow](https://github.com/rafaeljbgomes/ClinicFlow). DM-01
is tracked in [issue #1](https://github.com/rafaeljbgomes/ClinicFlow/issues/1),
and DM-02 is tracked in
[issue #6](https://github.com/rafaeljbgomes/ClinicFlow/issues/6).
