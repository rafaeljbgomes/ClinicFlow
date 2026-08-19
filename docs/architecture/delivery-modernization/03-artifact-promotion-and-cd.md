# DM-03: Immutable Artifact Promotion and Per-Service CD

## Problem

ClinicFlow has no image registry, promotion flow, environment credentials, or
automated deployment. Adding deployment stages before defining those boundaries
would produce non-reproducible releases and broad credentials.

## Outcome

Every successful service build publishes one immutable container image. A
separate, auditable deployment action promotes that exact image to one target
environment and one service release, verifies it, and can roll it back without
changing other services.

## Scope

- Select an image registry and repository naming convention.
- Define an immutable image identity: content digest plus a commit-derived tag
  for human navigation.
- Define environments, promotion authority, Jenkins credentials, and least
  privilege for deployment.
- Add per-service deployment, rollout verification, and rollback stages after
  the service CI quality gate.
- Produce image metadata, SBOM, and vulnerability-scan evidence appropriate to
  the selected registry and CI runner.
- Define a deployment record linking commit, image digest, Helm release,
  namespace, environment, and verification result.

## Out of Scope

- Replacing shared Kubernetes or observability infrastructure.
- Releasing every service together as a unit.
- Automatically promoting unreviewed builds to production.
- Creating production persistence, ingress, or secret-manager infrastructure
  without an approved environment design.

## Prerequisites

- DM-01: individual Helm release and image-reference boundary.
- DM-02: service CI jobs producing the candidate image.
- An approved registry, at least one target environment, and deployer identity.
- A secret-management and access-control decision for the target environment.

## Decisions Required Before Implementation

- Registry provider and retention policy.
- Development, staging, and production environment model.
- Promotion method: manual approval, GitOps pull request, or controlled Jenkins
  deployment action.
- Deployment authentication and namespace/service RBAC.
- Required verification: readiness, smoke checks, error-rate window, and
  rollback trigger.

## Acceptance Criteria

- An artifact built from a commit is discoverable by immutable digest.
- Deploying `clinicflow-patient` references that exact digest and leaves the
  other service releases unchanged.
- The deployment identity and verification result are recorded by the pipeline.
- A failed rollout automatically stops and a documented command or stage rolls
  back only the affected release.
- No registry credential or cluster-admin credential is exposed to unrelated
  service jobs.

## Rollback Boundary

Do not overwrite a tag that has been promoted. Rollback always reuses a
previously verified immutable image and Helm revision. A deployment failure
must leave the previous service revision available before another promotion is
attempted.
