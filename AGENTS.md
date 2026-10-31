# Agent guide

Instructions for coding agents working in this repository. Human contributors
follow the same rules in [CONTRIBUTING.md](CONTRIBUTING.md).

## Project boundaries

- ClinicFlow is a local, synthetic-data portfolio prototype. Never add real
  patient data, production credentials or claims of production readiness.
- Demo and test credentials are intentionally public and local-only. Do not
  invent new secrets; generate local key material with the scripts in
  `scripts/`.
- Security issues in ClinicFlow's own code follow [SECURITY.md](SECURITY.md):
  do not describe them in public issues, PRs or commit messages.

## Layout

| Path | Contents |
| --- | --- |
| `*-service/` | Spring Boot 4 services (Java 21, Spring Framework 7, Jackson 3, Clean Architecture, MapStruct, Flyway, PostgreSQL per service). |
| `event-contracts/` | Shared RabbitMQ event payload contracts. |
| `frontend/` | Next.js App Router UI and same-origin BFF. Read `frontend/AGENTS.md` before frontend changes. |
| `deploy/` | Helm charts, gateway (Traefik) configuration, Jenkins and security tooling. |
| `scripts/ci/` | The CI entry points shared by GitHub Actions and Jenkins. |
| `.github/` | GitHub Actions workflows, the shared setup action and Dependabot. |
| `.ci/` | Pinned CI tools, security policy, vulnerability and ZAP exceptions. |
| `docs/` | Architecture decisions, development, infrastructure and security guides. |

## Working rules

- Keep changes focused. Leave unrelated working-tree changes alone, and keep the
  existing layout and design.
- Read the relevant ADR in `docs/architecture/` before changing service
  boundaries, messaging, security or delivery behaviour, and add an ADR when a
  change reverses or extends one.
- Tests follow Arrange-Act-Assert with one objective per test
  ([testing strategy](docs/development/testing-strategy.md)). Behavioural
  domain and application code must keep PIT at 90% mutation coverage and a 75%
  mutation score.
- CI logic belongs in `scripts/ci`, not in workflow YAML. Workflows call
  scripts with explicit parameters so each step can be reproduced locally.
- Change routing for CI lives in `scripts/ci/security-policy.cjs`. Update its
  tests in `scripts/ci/tests/` when you change which paths affect which
  component.
- Pin new GitHub Actions to full commit SHAs with a version comment, and new CI
  tools in `.ci/tools.json` with their SHA-256. Keep workflow permissions
  read-only unless a job needs more, and pass PR-controlled values to scripts
  through `env:`, never `${{ }}` inside `run:`.
- Never weaken a gate to make a check pass: no blanket scanner ignores, no
  lowered coverage or mutation thresholds, no skipped tests. Vulnerability and
  ZAP exceptions need a separate, reviewed policy-only PR
  ([gate policy](docs/development/pr-quality-security.md)).

## Validate before proposing a change

Run the checks for what you touched (PowerShell 7 from the repository root):

```powershell
.\mvnw.cmd -pl :patient-service -am verify          # backend service, needs Docker
cd frontend; npm ci; npm run test:unit; npm run lint; npm run typecheck; npm run build
.\scripts\ci\Test-RepositoryHygiene.ps1               # Betterleaks and tracked-path hygiene
.\scripts\ci\Test-Workflows.ps1                       # actionlint and zizmor, after .github changes
node --test scripts/ci/tests/*.test.cjs               # after routing or policy changes
.\scripts\ci\Test-Platform.ps1                        # after Helm, gateway or Compose changes
```

Say which checks you ran and which you could not run. A green local subset is
not full acceptance; the seven `clinicflow/*` GitHub Actions checks are.

## Git and pull requests

- Branches use conventional prefixes (`feat/`, `fix/`, `chore/`, `perf/`,
  `docs/`, `test/`, `ci/`). Commits follow Conventional Commits, with
  implementation and documentation in separate commits.
- Do not push, merge or change repository settings unless the maintainer asks.
  PRs are merged with merge commits, not squashed.
- Write multi-line PR bodies to a file and pass `--body-file`.
- Commits and PRs carry no AI attribution or co-author trailers.
