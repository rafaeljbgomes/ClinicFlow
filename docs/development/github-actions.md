# GitHub Actions CI

GitHub Actions is the PR gate of record. It runs on GitHub-hosted runners, so
pull requests are validated without a workstation-hosted Jenkins. The local
Jenkins pipelines remain available as an on-demand runner; both engines call the
same `scripts/ci` entry points, so a stage fails or passes the same way in either.

## Workflows and required checks

| Workflow | Final check | Scope |
| --- | --- | --- |
| `repository.yml` | `clinicflow/repository` | Secret and path hygiene, CI policy regression tests, configuration parsing, actionlint and zizmor. Runs for every change. |
| `auth-service.yml` … `notification-service.yml` | `clinicflow/auth` … `clinicflow/notification` | Build, unit and integration tests, JaCoCo evidence, SonarQube Cloud, SBOM/dependency scan, image build and scan, Dockerfile scan, PIT outside PRs. |
| `frontend.yml` | `clinicflow/frontend` | `npm ci`, Vitest with LCOV, lint, type check, production build, standalone package, SonarQube Cloud, dependency/image/Dockerfile scans. |
| `system.yml` | `clinicflow/system` | Helm/kubeconform platform validation, gateway contract (HTTP and TLS), composed stack with Playwright, platform configuration and image scans, authenticated HTTPS ZAP DAST. |

The five service workflows are thin callers of `_backend-service.yml`. Every
workflow starts with `_route.yml`, which runs `security-policy.cjs prepare` to
decide whether the change affects the component. Routing is shared with Jenkins
and covered by `scripts/ci/tests/security-policy.test.cjs`:

- Documentation and image-only changes affect no component, but
  `clinicflow/repository` still runs.
- A component's own paths select that component. Backend `src/main` or
  Dockerfile changes also request DAST.
- `event-contracts/` selects the four consuming services and full-system
  acceptance.
- Frontend BFF, server, proxy, E2E, Dockerfile and dependency changes request
  full-system acceptance and DAST.
- Anything else, including `.github/`, `scripts/ci/`, `deploy/`, Compose files
  and `pom.xml`, selects every component and the full system.

Every workflow triggers on every PR. When a component is unaffected, its jobs
are skipped and the final `clinicflow/<component>` job succeeds with "Not
affected by this change". This matters because GitHub leaves checks from a
workflow skipped by `on.paths` pending forever, and a pending required check
blocks the merge. The final job fails when routing fails or any selected job
fails or is cancelled. Bind required checks to these eight names only.

System acceptance does not repeat the backend and frontend suites. Any change
that needs full-system acceptance also selects the affected component
workflows, which run those suites in parallel.

## Events

| Event | Behaviour |
| --- | --- |
| `pull_request` to `main` | Routed from the merge base. PIT is skipped. Superseded runs for the same PR are cancelled. |
| `push` to `main` | Routed from `github.event.before`. PIT runs for affected services. Trivy SARIF is uploaded to code scanning. Sonar analyses `main`, which refreshes the PR baseline. |
| `schedule` (02:15 UTC components, 03:00 UTC system) | Forces every component, PIT, full-system acceptance and the broader ZAP sweep. |
| `workflow_dispatch` | Same as the schedule, on demand. |

Mutation testing is deliberately absent from PRs: PIT is the slowest stage and
its thresholds (90% mutation coverage, 75% score) are still enforced on `main`
and nightly. Run it locally before merging behaviour-heavy domain changes:

```powershell
.\scripts\ci\Invoke-ServiceBackend.ps1 -Service clinical-service -Phase Mutation
```

## Toolchain and hardening

- Runners are pinned to `ubuntu-24.04`. Java 21 (Temurin) and Node.js 24.19.0
  come from `setup-java`/`setup-node` with Maven and npm caches.
- Helm, kubeconform, Trivy, Betterleaks, actionlint, zizmor and the Sonar
  scanner are installed by `Install-CiTools.ps1` from `.ci/tools.json`.
  Each download is verified against its SHA-256 before extraction and cached by
  manifest hash. Update a tool by changing its version and checksum together.
- Third-party actions are pinned to full commit SHAs. Dependabot proposes
  grouped updates weekly after a seven-day cooldown.
- Workflows default to `contents: read`. Only the scan jobs add
  `security-events: write`, and SARIF is uploaded only from `main` and
  scheduled runs. Checkout never persists credentials.
- Workflows use `pull_request`, never `pull_request_target`. Fork PRs run with a
  read-only token and no secrets; Sonar reports a notice and runs after merge.
- PR-controlled values such as branch names reach scripts only through
  environment variables, never through `${{ }}` interpolation inside `run:`.
- The daily Trivy database is cached. The policy refuses a vulnerability
  database older than 24 hours, so a feed outage fails closed rather than
  scanning stale data. The Java database only maps JAR checksums to Maven
  coordinates, and Trivy refreshes it three days after each build, so its limit
  is 96 hours.

## Evidence

Each job writes a summary to its run page (test counts, failed tests, JaCoCo or
LCOV coverage, PIT score) and uploads artifacts for 14 days: Surefire/Failsafe
XML, JaCoCo, the service JAR, PIT reports, Vitest/LCOV output, the frontend
standalone package, Playwright traces, rendered manifests, gateway contract
output, and `reports/security` (Trivy JSON and gate dispositions, SBOMs,
image IDs, Sonar gate results, sanitized ZAP output).

## Debugging workflow changes

Workflow YAML is kept as thin glue, so most failures are script failures that
can be reproduced locally exactly as before. Work from the cheapest loop
outward.

1. **Run the failing script locally.** Every step is a `scripts/ci` call with
   explicit parameters, for example
   `.\scripts\ci\Invoke-ServiceBackend.ps1 -Service auth-service -Phase Integration`.
   The run page shows the exact command for each step.
2. **Lint before pushing.** `.\scripts\ci\Test-Workflows.ps1` runs actionlint
   (with shellcheck) and zizmor. It uses local binaries when present and
   otherwise the pinned container images, so Docker alone is enough. It catches
   YAML, expression, `needs`/output, shell quoting and workflow-security
   mistakes in seconds.
3. **Exercise routing locally.** Reproduce what `_route.yml` decides:

   ```powershell
   $env:CHANGE_ID = '1'; $env:CHANGE_TARGET = 'main'
   node scripts/ci/security-policy.cjs prepare system
   Get-Content reports/security/context.json
   ```

4. **Optionally use `act` for glue logic.** [nektos/act](https://nektosact.com)
   runs individual jobs in Docker, for example
   `act pull_request -j route -W .github/workflows/_route.yml`. It works well
   for conditions, outputs and routing. Its runner images differ from GitHub's,
   and Docker-in-Docker networking makes Compose, Testcontainers and DAST jobs
   unreliable, so treat it as a smoke test, not acceptance.
5. **Iterate on a draft PR.** Real runs are the acceptance environment:

   ```powershell
   gh pr checks --watch
   gh run list --branch <branch> --limit 10
   gh run view <run-id> --log-failed
   gh run rerun <run-id> --failed --debug
   gh run download <run-id> --name system-dast-evidence
   ```

   `--debug` re-runs with runner and step debug logging. Before marking the PR
   ready, fold fix-ups together with `git commit --fixup <sha>` and
   `git rebase -i --autosquash origin/main`, because PRs are merged with merge
   commits and keep their history.
6. **Manual runs after merge.** `workflow_dispatch` only exists once a workflow
   is on `main`. Afterwards, `gh workflow run system.yml --ref main` starts a
   forced run.

Because routing treats `.github/` and `scripts/ci/` changes as affecting
everything, a CI change is validated by all eight checks before it can merge.

## Activation

These steps change repository or external settings and are done by the
maintainer:

1. **Security mode.** Workflows enforce by default. Setting the repository
   variable `CLINICFLOW_SECURITY_MODE=report` turns scanner and quality-gate
   violations into warnings while initial findings are triaged. Scanner and
   configuration errors still fail. Remove the variable to enforce again.
2. **SonarQube Cloud.** Sonar stays inactive, with a warning on each run, until
   the repository variable `SONAR_ORGANIZATION` exists. Import the public
   repository into a SonarQube Cloud organization as a monorepo with six
   projects keyed `rafaeljbgomes_clinicflow_<auth|patient|appointment|clinical|notification|frontend>`,
   disable automatic analysis, and add the `SONAR_TOKEN` secret. Then run each
   component workflow once on `main` (`gh workflow run auth-service.yml --ref main`)
   so PR analyses have a fresh baseline. From then on a missing token fails
   closed on same-repository PRs.
3. **Code scanning and Dependabot.** Enable Dependabot alerts and security
   updates. Code scanning accepts the Trivy SARIF uploads without CodeQL.
4. **Required checks.** After all eight checks pass on a commit, generate a
   reviewable ruleset proposal that binds the checks to the GitHub Actions app:

   ```powershell
   .\scripts\ci\Set-RequiredQualityChecks.ps1 -VerifiedCommit <40-character-sha>
   ```

   Review `reports/security/ruleset-proposal.json`, then rerun with `-Apply`.
   The proposal preserves the existing rules and bypass list. It does not
   require branches to be up to date (`-Strict`): this is a single-maintainer
   repository merged with merge commits, so stale-base breakage is rare, and the
   `push` run on `main` re-validates every merge result. Add `-Strict` if
   parallel PRs become common.

See [PR quality and security gates](pr-quality-security.md) for the vulnerability
policy, exception workflow and DAST scope.
