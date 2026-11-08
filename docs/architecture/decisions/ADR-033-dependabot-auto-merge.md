# ADR-033: Dependabot minor and patch updates merge automatically

Status: accepted. Date: 2026-11-08. Extends ADR-022.

## Context

Dependabot PRs were merged by hand and piled up. A major bump opened one PR per
directory or Dockerfile, so one Spring Boot upgrade was seven PRs and one
Temurin upgrade was four. Routine fixes waited behind that noise, including
security updates for open alerts.

The seven required `clinicflow/*` checks already decide whether a change is safe
to merge, so a person repeating that decision for a patch adds little.

## Decision

Dependabot PRs are grouped so that each ecosystem produces few of them:
security, minor and patch, and major updates each get their own group, and
Docker updates produce one PR per image.

A workflow queues minor and patch PRs for GitHub auto-merge with a merge commit.
The ruleset is unchanged and has no bypass actors, so the required checks still
decide the merge. These stay manual:

- major updates;
- updates whose type Dependabot does not report;
- Keycloak, whose minors need the upgrading guide (ADR-027) and whose version is
  also pinned in the chart values, which the Docker ecosystem does not update.

Dependabot no longer proposes Temurin majors, because Java 21 is the LTS
baseline, or the `maven` build image, because it presents a newer JDK tag suffix
as a non-major change.

The seven-day cooldown stays on version updates as protection against a
compromised or broken release reaching `main` unreviewed. Security updates do
not have a cooldown.

The merge uses a fine-grained token stored as a Dependabot secret instead of
`GITHUB_TOKEN`, because events caused by `GITHUB_TOKEN` do not start workflows
and the `push` run on `main` is what validates each merge result. The workflow
runs on `pull_request`, only for PRs opened by Dependabot, and executes the
policy from the base commit.

## Consequences

- Minor and patch dependency changes reach `main` without a human reading them,
  so the required checks are the only review. A minor release that breaks
  behaviour the tests do not cover will land.
- Required checks do not require up-to-date branches, so two auto-merged PRs can
  combine badly. The `push` run on `main` detects that after the merge.
- The token is one more credential to rotate. Without it, eligible PRs stay open
  and the workflow fails visibly.
- The `maven` build image is bumped by hand.
