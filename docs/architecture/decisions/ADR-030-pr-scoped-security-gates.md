# ADR-030: PR security gates block only what the PR adds

Status: implemented. Date: 2026-10-25. Extends ADR-022.

## Context

The Trivy and ZAP gates judged every PR on the whole state of the component it
touched. A CVE published against a dependency already on `main`, an expired
acceptance, or an intermittent DAST alert on `main` therefore failed every PR
that routed to that component, whatever the PR changed. Shared paths such as
`scripts/ci/`, `deploy/` or the frontend lockfile route to every component, so
in practice one upstream advisory blocked all open PRs. Fix PRs for separate
advisories stayed red on each other's findings, and on 2026-10-22 the whole
repository had to drop to `CLINICFLOW_SECURITY_MODE=report` to merge them.

Betterleaks had the same shape: `betterleaks git .` scans every fetched ref, so
a leak-like commit on one open branch failed `clinicflow/repository` on all of
them.

## Decision

Every PR scan runs exactly as before, but a High/Critical finding is
**inherited**, not blocking, when `main`'s latest scan of the same component and
target already had it:

- vulnerabilities: the same package at the same installed version is in
  `main`'s inventory (Trivy runs with `--list-all-pkgs`), whatever the CVE;
- configuration: the same rule on the same file;
- ZAP: the same plugin, origin (`api`/`ui`) and path.

`scripts/ci/Get-SecurityReference.ps1` downloads `main`'s evidence artifact for
the job into `reports/reference/` before the scan. It trusts only artifacts
from `push`, `schedule` or `workflow_dispatch` runs on `main` in this
repository. The scan jobs get `actions: read` for this. If no reference can be
fetched, or it is malformed, nothing is inherited and every High/Critical
finding blocks as before. The gate therefore fails closed.

Inherited findings stay in `*.gate.json` with disposition `inherited`.
`main`, nightly and manual runs never inherit: they keep the full gate. So a
finding that arrives on `main` still turns `main` red, and it is fixed or
accepted there through the existing policy-only PR process. Reviewed exceptions
still apply first, so `baselined` wins over `inherited`.

Betterleaks scans `<merge-base>..HEAD` on PRs and the history of `HEAD` on
`main`, not every fetched ref.

## Consequences

- A PR fails only for a package version, configuration finding or ZAP alert it
  introduces. Bumping a vulnerable package to another vulnerable version still
  blocks, because the version differs.
- Upstream CVEs on `main` no longer block unrelated work. They show up on the
  `main` and nightly runs, in code scanning (SARIF) and as `inherited` in PR
  evidence, and they still need a fix or a reviewed exception before they
  expire from attention. The 30-day exception rules are unchanged.
- The reference is only as fresh as the last `main` run of that job. Nightly
  sweeps keep it within a day. A stale reference can only make a PR stricter.
  A package that `main` gained after its last scan counts as new.
- Removing a package from `main` and re-adding it in a PR counts as new until
  the next `main` scan.
- A leak committed to a branch with no PR is no longer reported by other
  branches' runs. GitHub secret scanning and that branch's own PR still cover it.
- Local Jenkins runs have no reference and keep the full gate.
