# PR quality and security gates

The component and system pipelines add SonarQube Cloud, Trivy dependency,
image and configuration scans, and authenticated HTTPS ZAP DAST to the existing
tests, coverage and PIT thresholds. The scanners never replace those checks.
[GitHub Actions CI](github-actions.md) describes the workflows, routing,
required check names and activation steps. This page describes the policy the
scanners enforce.

## Modes

`CLINICFLOW_SECURITY_MODE` controls what a policy violation does. GitHub
Actions defaults it to `enforce`; the repository variable can set `report`
while initial findings are triaged.

- `enforce`: new High/Critical findings, unexcepted High ZAP alerts and failed
  Sonar quality gates fail the job.
- `report`: the same violations are written to
  `reports/security/violations.txt` and raised as workflow warnings.

Scanner execution errors, unrecognized targets, empty dependency inventories,
malformed reports, missing or stale databases and incomplete DAST coverage fail
in both modes. A report-mode success is not evidence that a change is clean.

## Vulnerability policy

Trivy is checksum-pinned in `.ci/tools.json`. It scans:

- each backend's resolved CycloneDX 1.6 JSON BOM, including test dependencies;
- the frontend package lock, including development dependencies;
- each final application image by its local immutable image ID;
- component Dockerfiles, rendered Helm manifests and the images of the running
  system stack, including vendor images.

The dependency job prepares the selected reactor with a separate test-skipped
Maven install. The image scan complements the source inventories: base-OS
packages and shipped dependencies can differ.

Every finding stays in the JSON reports. New High/Critical findings block,
including vulnerabilities without fixes. Low/Medium findings are reported.
There is no blanket `ignore-unfixed` and no automatically accepted baseline.
The committed empty baselines are enrollment placeholders, not an assertion that
the current dependencies are vulnerability-free.

The Trivy database is restored from a daily cache and refreshed before scans.
If the refresh fails, a cached vulnerability database is used only while its
upstream update timestamp is within 24 hours. The Java database, which holds no
vulnerability data, follows Trivy's three-day refresh and is accepted for up to
96 hours. Results from `main` and scheduled runs are also
uploaded as SARIF to the repository's code scanning view.

### Reviewed exceptions

The vulnerability baseline is `.ci/security-baseline.json`. Its entries use:

```json
{
  "component": "auth-service",
  "target": "dependencies",
  "id": "CVE-REPLACE-WITH-AN-ACTUAL-FINDING",
  "package": "exact-package-name",
  "version": "exact-installed-version",
  "owner": "responsible-maintainer",
  "reason": "Evidence and reason for temporary acceptance",
  "issue": "https://github.com/rafaeljbgomes/ClinicFlow/issues/REPLACE",
  "reviewedAt": "YYYY-MM-DD",
  "expiresAt": "YYYY-MM-DD"
}
```

Copy the exact identity fields from the normalized `*.gate.json` report rather
than guessing them. Each acceptance expires within 30 days. Additions and
renewals must match findings observed on the current `main` state. Expired
findings block again, and a dependency-version change cannot inherit an
acceptance for another version. Issues may track public upstream CVEs in
dependencies; vulnerabilities in ClinicFlow's own code follow `SECURITY.md` and
are never disclosed in public issues.

Ordinary PRs load acceptances from `main`, not from their proposed files.
Additions and renewals need a separate policy-only PR with normal review;
mixing source or dependency changes with exception edits is rejected.

ZAP exceptions in `.ci/zap-exceptions.json` use the same metadata with
`component=dast`, target `api` or `ui`, ID `ZAP-<pluginId>`, package equal to
the exact affected URL path, and version `-`. The scope is shared across roles,
because ZAP's consolidated alert list cannot attribute an alert to one role.

## SonarQube Cloud

Each deployable component is a separate SonarQube Cloud project using the
built-in Sonar way gate. Backend analysis imports the merged unit and
integration JaCoCo XML; frontend analysis imports Vitest LCOV. The scanner waits
up to five minutes for the gate, then the result is verified through the
compute-task and quality-gate APIs instead of trusting the scanner exit code
alone.

PR analysis compares against the last `main` analysis. If that analysis is
missing, or the component's source or shared build paths changed since it, the
gate fails and asks for a fresh `main` run. A stale baseline must never silently
redefine what counts as new code. Review new security hotspots in SonarQube
Cloud before rerunning the gate.

## Runtime security scanning

The security Compose override uses fresh named volumes, generated short-lived
TLS certificates, synthetic bootstrap identities, real gateway hostnames and
secure BFF cookies. It publishes no host ports and mounts no workstation
secrets. Active scans mutate only disposable synthetic data.

ZAP's pinned image runs the Automation Framework. Discovery exercises the bearer
APIs and the corresponding BFF routes as psychologist, patient and
administrator, plus psychologist SSR pages and representative JSON writes. The
scanner reads the synthetic password from the bootstrap fixture that creates the
disposable accounts. The request manifest is CI-only; no production Swagger
endpoint is added. This is bounded API/BFF and HTML coverage, not exhaustive
browser or business-logic testing, so the role, ownership and CSRF Playwright
tests remain necessary.

Bearer tokens and BFF sessions are renewed periodically. The runner verifies
certificate identity and protected API/BFF access before and after each role
scan, and rejects automation errors or warnings and missing endpoint coverage.
A diagnostic smoke scan cannot satisfy the gate. High ZAP alerts block unless
narrowly excepted; Medium/Low alerts remain visible.

PR scans cover auth-service plus the affected components, with a two-minute
active-scan budget per role. Scheduled and manual runs scan every component with
a twelve-minute budget per role. A scan the budget stops still has to end
normally, and the coverage report records how far it got; a scan that is still
running at the outer timeout, or ends in any other state, fails. Sensitive ZAP sessions,
requests, response bodies and cookies stay inside the disposable container;
only sanitized findings and coverage are uploaded.

## Local validation

```powershell
node --test scripts/ci/tests/*.test.cjs
.\scripts\ci\tests\Test-TrivyCache.ps1
python -m unittest discover -s scripts/ci/tests -p test_dast.py -v
.\scripts\ci\Test-SecurityConfiguration.ps1
.\scripts\ci\Test-Workflows.ps1
```

The scanner scripts also run locally when Trivy, the Sonar scanner and Docker
are installed. Set `TRIVY_CACHE_DIR` outside the repository and
`CLINICFLOW_SECURITY_MODE` first, then run, for example,
`.\scripts\ci\Invoke-ComponentSecurity.ps1 -Component auth-service -Phase Dependencies`
after `node scripts/ci/security-policy.cjs prepare auth-service --force`.

## Sources

- [SonarQube Cloud monorepos](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/monorepo-support)
- [Trivy SBOM scanning](https://trivy.dev/docs/latest/target/sbom/)
- [CycloneDX Maven plugin](https://cyclonedx.github.io/cyclonedx-maven-plugin/)
- [ZAP Automation Framework](https://www.zaproxy.org/docs/automate/automation-framework/)
- [Uploading SARIF to GitHub](https://docs.github.com/en/code-security/code-scanning/integrating-with-code-scanning/uploading-a-sarif-file-to-github)
