# DM-05: Focused System Validation Gate

## Problem

Independent pipelines must not remove evidence for cross-service behavior. The
current root Jenkins pipeline provides broad validation, but it needs an
explicit role once service-scoped CI exists: prove contracts, deployment
composition, and critical end-to-end journeys rather than act as every
service's only CI path.

## Outcome

A source-controlled system-validation pipeline runs only when shared changes or
promotion policy require it, while still providing a reliable safety net for
the full ClinicFlow workflow.

## Scope

- Rename or replace the root Jenkinsfile as `Jenkinsfile.system` only after
  service jobs are established.
- Define triggers for system validation: `event-contracts`, root Maven
  configuration, Dockerfiles, Compose, Helm, CI scripts, BFF routing, shared
  authentication, and scheduled runs.
- Retain targeted checks for repository hygiene, Helm rendering and schema
  validation, Compose startup, and critical Playwright journeys.
- Add provider/consumer or compatibility tests for HTTP and RabbitMQ contracts
  where version skew can occur.
- Publish a concise system evidence set: component versions, image digests,
  test reports, rendered manifests, and Compose diagnostics on failure.

## Out of Scope

- Running full-stack browser tests for every isolated service source edit.
- Deployment to a persistent environment (DM-03).
- Replacing service-level unit and integration tests.
- Defining new product workflows.

## Prerequisites

- DM-01 provides independently installable releases.
- DM-02 makes service CI responsible for local build/test evidence.
- Contract ownership and compatibility rules are documented for
  `event-contracts` and public HTTP APIs.

## Acceptance Criteria

- A service-only change is covered by its service pipeline without requiring a
  full system run.
- A change to a shared contract or deployment asset triggers the system gate.
- The system gate can compose a known set of independently built image
  versions, rather than relying on a single application-wide tag.
- A critical browser journey and one RabbitMQ-driven workflow are verified with
  results and failure diagnostics retained.
- A failed system run identifies the selected component versions and does not
  silently deploy or mutate a persistent environment.

## Rollback Boundary

Preserve the existing root Jenkins pipeline until the replacement covers the
same supported local Compose and Playwright workflow from a clean checkout.
This work package changes verification orchestration, not service runtime
behavior.
