# ADR-022: GitHub Actions as the PR gate of record

Status: implemented; required checks are activated after live acceptance. Date: 2026-10-20.
Amended by ADR-031: `auth-service` and `clinicflow/auth` were retired, leaving four backend workflows
and seven required checks.

## Context

DM-02 split CI into independently executable Jenkins pipelines that run on a
workstation-hosted controller and agent. That Jenkins polls the repository, so
PRs are validated only while the workstation runs it, and it needs a GitHub App
to publish check results. The repository is public, so GitHub-hosted runners
cost nothing and report checks natively. The unpublished PR-gate work (Sonar,
Trivy, ZAP, required checks) had been designed around that local Jenkins.

## Decision

Run PR, `main`, nightly and manual validation on GitHub Actions:

- One workflow per deployable component, plus `system` and `repository`. The
  five backend workflows call one reusable pipeline.
- Workflows are glue around the existing `scripts/ci` entry points, which stay
  the single implementation shared with Jenkins.
- Each workflow routes the change with `security-policy.cjs`, runs or skips its
  jobs, and always reports one final `clinicflow/<component>` check. Required
  checks bind those eight names to the GitHub Actions app.
- Independent stages run as parallel jobs. PIT runs on `main`, nightly and on
  demand, not on PRs.
- System acceptance keeps the cross-service evidence (platform rendering,
  gateway contract, Compose plus Playwright, platform scans, DAST) and no longer
  repeats component suites, which routing always selects for shared changes.
- Tools are checksum-pinned in `.ci/tools.json`; actions are SHA-pinned;
  permissions default to read-only; fork PRs get no secrets.

Jenkins remains the on-demand local runner with its current pipelines.

## Consequences

- PR feedback no longer depends on a local machine, and the components validate
  in parallel instead of sequentially on one agent.
- Changes to workflows cannot be run before they are pushed. The mitigation is
  to keep logic in scripts, lint workflows locally with actionlint and zizmor,
  and iterate on draft PRs (see `docs/development/github-actions.md`).
- PRs no longer prove PIT thresholds. A regression in mutation score is caught on
  `main` or by the nightly run instead of before merge.
- Hosted runners are ephemeral, so caches (Maven, npm, Playwright, Trivy
  database, tools) replace the agent's persistent volumes, and the Trivy
  database freshness rule keeps cache use bounded.
- SonarQube Cloud and required checks need maintainer-owned activation; until
  then the workflows report Sonar as inactive and merging is not blocked.
- Jenkins and Actions definitions can drift. They share scripts and routing, but
  the Jenkinsfiles do not yet run the security stages.
