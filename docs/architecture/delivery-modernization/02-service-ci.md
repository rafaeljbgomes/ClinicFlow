# DM-02: Independent Service-Scoped Jenkins CI

Tracked by [GitHub issue #6](https://github.com/rafaeljbgomes/ClinicFlow/issues/6).

## Problem

The legacy root `Jenkinsfile` is a useful system-quality pipeline, but normal
service changes invoke full-repository backend, frontend, platform, container,
Compose, and browser validation. It does not provide an independently owned CI
path for a service.

## Outcome

Each deployable component has an independently executable, source-controlled
Jenkins pipeline. Backend services own their build, unit, integration,
mutation, package, coverage, reports, and local-image evidence. The frontend
owns its dependency install, unit tests, static analysis, production build,
standalone package, and local-image evidence. `Jenkinsfile.system` retains the
cross-service Compose and Playwright journey.

The implementation uses these pipeline definitions:

```text
Jenkinsfile.auth
Jenkinsfile.patient
Jenkinsfile.appointment
Jenkinsfile.clinical
Jenkinsfile.notification
Jenkinsfile.frontend
Jenkinsfile.system
```

The physical architecture remains one local controller and the existing
`clinicflow-ci` agent. Pipeline independence does not mean one controller,
machine, or cluster per service.

## Backend Lifecycle Contract

Every backend pipeline exposes distinct Jenkins stages for Checkout,
Repository hygiene, Build, Unit tests, Integration tests, Mutation tests,
Package, and Image build.

`scripts/ci/Invoke-ServiceBackend.ps1` accepts only the five service names and
the phases `Build`, `Unit`, `Integration`, or `Mutation`:

- Build runs `clean compile` for `-pl :<service> -am` without tests.
- Unit runs `test` in the same workspace and publishes only the selected
  service's Surefire XML.
- Integration runs `verify -Dskip.unit.tests=true` without cleaning. The new
  property skips Surefire only, so Failsafe runs the service-local
  Testcontainers suites and JaCoCo merges the preserved unit and integration
  execution data.
- Mutation runs scoped PIT after `test-compile`, excludes `*IT`, and preserves
  the 90% mutation-class coverage and 75% mutation-score thresholds.
- Package validates and archives exactly one executable Spring Boot JAR plus
  that service's JaCoCo and PIT reports; it does not invoke another build.
- Image build uses only that component's Dockerfile, assigns a build-specific
  local tag, never pushes it, and removes the tag in pipeline cleanup.

Auth selects only the root POM and `auth-service`. Patient, appointment,
clinical, and notification additionally select `event-contracts` through
their declared Maven test dependency. No service pipeline selects another
service or `coverage-report`.

## Frontend Lifecycle Contract

`Jenkinsfile.frontend` exposes Checkout, Repository hygiene, Install
dependencies, Unit tests, Static analysis, Build, Package, and Image build as
separate stages. `scripts/ci/Invoke-FrontendPhase.ps1` allow-lists `Install`,
`Unit`, `Quality`, `Build`, and `Package`. Package creates a standalone archive
containing the Next.js server, static output, and public assets. Playwright is
not a component responsibility because it validates the composed system.

## Jenkins Jobs and Triggers

JCasC generates these fixed jobs from one component map:

- `clinicflow-auth-ci`
- `clinicflow-patient-ci`
- `clinicflow-appointment-ci`
- `clinicflow-clinical-ci`
- `clinicflow-notification-ci`
- `clinicflow-frontend-ci`
- `clinicflow-system-ci`

Each component pipeline has its own workspace, a 60-minute timeout, 20 retained
builds, 10 retained artifact sets, component-only reports and artifacts, and
no upstream or downstream service-job dependency. The system pipeline retains
the same 60-minute timeout while keeping Compose and browser validation in its
own distinct stages.

All seven jobs poll SCM every five minutes. Git PathRestriction allow-lists
implement these boundaries:

- Service source or its Jenkinsfile triggers that service only.
- `event-contracts/**` triggers patient, appointment, clinical, notification,
  and system as separate jobs.
- Root Maven configuration and wrapper changes trigger every backend and
  system job.
- Frontend changes trigger frontend; BFF/API routes, server authentication,
  Playwright assets, frontend build configuration, and the frontend Dockerfile
  additionally trigger system.
- A component Dockerfile triggers that component and system.
- Shared backend/frontend CI mechanics and the agent toolchain trigger their
  respective component set plus system.
- Helm, Compose, Docker bootstrap, controller/JCasC, and system-script changes
  trigger system.
- Repository-hygiene logic triggers every job.

Path restrictions apply to SCM polling only. A manual build always executes
the selected job.

## System Pipeline Boundary

`Jenkinsfile.system` has distinct stages for Checkout, Repository hygiene,
shared/backend unit validation, frontend unit validation, frontend quality and
build, platform validation, shared/backend integration validation, system
image build, Compose environment, and Playwright end-to-end tests. Mutation is
absent because every backend service now owns it.

The system job publishes platform and browser evidence only. It does not
archive service JARs, service coverage, or service PIT reports, and ordinary
service-source changes do not require it.

## Out of Scope

- Pushing images, deployment, promotion, or rollback (DM-03).
- Changing Helm release packaging (DM-01).
- Adding a Jenkins plugin, Shared Library repository, registry, or deployment
  credential.
- Creating a controller, agent, or cloud account per service.
- Eliminating full-stack CI coverage.

## Acceptance Criteria

- Every backend service independently passes build, unit, integration,
  mutation, package, coverage, and image stages.
- A patient-only change does not compile, test, mutate, package, or image
  another service.
- A contract change starts each affected service job independently.
- Unit, integration, mutation, and browser tests remain separate Jenkins
  stages with separate reports.
- Every job publishes only artifacts and reports in its responsibility.
- Frontend UI-only changes do not require system validation; BFF and routing
  changes do.
- The full Compose/Playwright journey remains available through system CI.
- No pipeline pushes an image or changes a deployed environment.

## Activation and Rollback Boundary

All seven replacement jobs passed from clean checkouts, including the isolated
Compose and Playwright journey in `clinicflow-system-ci`. The activation commit
therefore disables `clinicflow-engineering-stabilization` and retires the root
`Jenkinsfile`. Roll back by re-enabling the legacy job and reverting the
activation commit; no registry or deployed environment is affected.
