# DM-04: Platform and Data-Access Boundaries

## Problem

The current Kubernetes chart deploys application workloads together with local
PostgreSQL, RabbitMQ, shared configuration, shared credentials, networking,
and dashboard assets. Although the services use separate databases, every
backend workload reads the same PostgreSQL username and password. This does not
enforce database ownership at the credential boundary.

## Outcome

Platform capabilities are managed separately from application service releases,
and each service receives only the configuration and credentials it needs.
Shared infrastructure remains shared where that is operationally appropriate.

## Scope

- Define application-owned versus platform-owned Helm resources.
- Give each service a distinct PostgreSQL login with access only to its own
  database/schema and migrations.
- Give each service separate Kubernetes Secrets or explicitly scoped external
  secret references.
- Define RabbitMQ permissions per service: publishing exchanges, consuming
  queues, and administration boundaries.
- Make service configuration references explicit and minimize shared ConfigMap
  injection.
- Retain network policies that document and enforce only the necessary service,
  broker, database, DNS, and monitoring traffic.

## Out of Scope

- A dedicated PostgreSQL instance, RabbitMQ broker, cluster, or VM per service.
- A cloud-provider decision, unless required by the chosen secret integration.
- Changes to domain schemas or event semantics.
- CD orchestration (DM-03).

## Touchpoints

- `deploy/helm/platform/templates/postgres.yaml`
- `deploy/helm/platform/templates/rabbitmq.yaml`
- `deploy/helm/platform/templates/secrets.yaml`
- `deploy/helm/platform/templates/networkpolicy.yaml`
- `deploy/helm/service/templates/configmap.yaml`
- `deploy/helm/service/templates/networkpolicy.yaml`
- `docker/postgres/init/**` and `docker/postgres/bootstrap/**`
- `docker-compose.yml` and `docker-compose.ci.yml`
- backend Kubernetes profiles

## Decisions Required Before Implementation

- Separate PostgreSQL databases versus schemas for the target environment.
- Which actor creates and rotates database users.
- Local-development parity strategy: development credentials may be simple, but
  production rules must not be weakened.
- Whether RabbitMQ users are per service and which permissions are granted.
- Whether secret delivery uses Kubernetes Secrets, External Secrets, Sealed
  Secrets, or a cloud secret manager.

## Acceptance Criteria

- A patient-service database credential cannot connect to or mutate the auth,
  appointment, clinical, or notification database.
- A service manifest does not receive unrelated database credentials.
- RabbitMQ permissions allow exactly the exchanges and queues required by the
  service's documented role.
- The full local Docker Compose workflow remains documented and functional.
- A fresh local installation or test environment proves that service startup,
  Flyway migration, health checks, messaging, and metrics still work.

## Rollback Boundary

Grant new credentials before removing the shared role. Migrate one service at a
time and validate login, Flyway, and messaging before revoking its old access.
Never rotate every service credential in the same unverified deployment.
