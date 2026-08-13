# DM-02: Service-Scoped Jenkins CI

## Problem

The current root `Jenkinsfile` is a useful system-quality pipeline, but normal
service changes invoke full-repository backend, frontend, platform, container,
Compose, and browser validation. It does not provide an independently owned CI
path for a service.

## Outcome

Each deployable component has one logical, source-controlled Jenkins pipeline
that can test and package only that component and its declared build
dependencies. Common delivery logic is reused through a versioned shared
library or small repository scripts, not copied between Jenkinsfiles.

Suggested repository layout:

```text
auth-service/Jenkinsfile
patient-service/Jenkinsfile
appointment-service/Jenkinsfile
clinical-service/Jenkinsfile
notification-service/Jenkinsfile
frontend/Jenkinsfile
Jenkinsfile.system
```

The physical Jenkins architecture remains one controller with isolated agents;
it is not one controller per service.

## Scope

- Define each service pipeline's checkout, unit test, integration test,
  package, image-build, test-result publishing, and artifact-retention stages.
- Build Maven services with the smallest valid Maven reactor selection, such as
  `-pl patient-service -am` when `event-contracts` is needed.
- Create separate Jenkins multibranch jobs or equivalent jobs pointing to each
  service Jenkinsfile.
- Extract common non-domain build behavior into shared scripts or a versioned
  Jenkins Shared Library.
- Preserve the root workflow as `Jenkinsfile.system` for cross-service checks.

## Out of Scope

- Pushing images, deployment, promotion, or rollback (DM-03).
- Changing Helm release packaging (DM-01).
- Creating a Jenkins controller, agent, or cloud account per service.
- Eliminating full-stack CI coverage.

## Touchpoints

- `Jenkinsfile`
- `deploy/jenkins/casc/jenkins.yaml`
- `deploy/jenkins/controller/plugins.txt`
- `scripts/ci/**`
- service directories and `frontend/`
- `docs/engineering-stabilization.md`

## Decisions Required Before Implementation

- Shared Library repository, or versioned shared code kept in this monorepo.
- Path-change triggering policy, including mandatory full runs for root POM,
  `event-contracts`, Docker, Helm, Compose, and shared-script changes.
- Which Testcontainers checks are required per service on every pull request.
- Whether the frontend has an independent build job but remains included in
  system browser tests.

## Acceptance Criteria

- A patient-only code change can execute a patient job without building the
  frontend or unrelated service images.
- Each service job publishes only its own test reports and build artifacts.
- A change in `event-contracts` triggers affected service jobs and the system
  validation job.
- A change in Helm, Compose, or shared pipeline code triggers system validation.
- The existing full Compose/Playwright scenario remains executable and reports
  results as a system pipeline.

## Rollback Boundary

Keep the original root job runnable until every new job is configured through
Jenkins Configuration as Code and has passed a clean checkout. Jenkinsfiles are
CI definitions only; this work package must not deploy an environment.
