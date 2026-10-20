# Contributing to ClinicFlow

ClinicFlow is a portfolio prototype maintained by one person. Issues and pull
requests are welcome, especially ones that improve tests, documentation,
security boundaries or delivery tooling. Coding agents should also read
[AGENTS.md](AGENTS.md).

## Ground rules

- Use only synthetic data. Never add real patient or health information.
- Report vulnerabilities privately as described in [SECURITY.md](SECURITY.md),
  not in public issues or pull requests.
- Discuss larger changes in an issue first, particularly anything that changes
  service boundaries, event contracts or security behaviour. Those decisions are
  recorded as [ADRs](docs/architecture/architectural-decisions.md).

## Set up

Follow [local development](docs/development/local-development.md). You need
Java 21, Node.js 24, PowerShell 7 and Docker. Integration tests use
Testcontainers and need a running Docker engine.

## Make a change

1. Branch from `main` with a conventional prefix: `feat/`, `fix/`, `chore/`,
   `perf/`, `docs/`, `test/` or `ci/`.
2. Keep the change focused and follow the existing structure: Clean
   Architecture ports per service, MapStruct mappers at boundaries, Flyway for
   schema changes.
3. Add or update tests. Tests use Arrange-Act-Assert with one objective each,
   as described in the [testing strategy](docs/development/testing-strategy.md).
   Domain and application logic must keep PIT at 90% mutation coverage and a
   75% mutation score.
4. Update documentation in the same pull request, in a separate commit.
5. Write commits in [Conventional Commits](https://www.conventionalcommits.org)
   form, for example `fix(clinical): reject edits to signed session notes`.

## Validate locally

Run what applies to your change:

```powershell
.\mvnw.cmd test                       # backend unit and slice tests
.\mvnw.cmd clean verify               # plus Testcontainers integration tests and coverage
cd frontend; npm ci; npm run test:unit; npm run lint; npm run typecheck; npm run build
.\scripts\ci\Test-RepositoryHygiene.ps1
.\scripts\ci\Test-Workflows.ps1       # when you change .github/
```

Mutation testing does not run on pull requests. If you change domain or
application behaviour, run it for the affected service before asking for review:

```powershell
.\scripts\ci\Invoke-ServiceBackend.ps1 -Service clinical-service -Phase Mutation
```

## Pull requests and CI

Open the pull request against `main`; a draft is fine while CI iterates.
GitHub Actions reports eight checks, one per component plus `system` and
`repository`. Unaffected components report "not affected" and pass. All eight
must pass before merging; see [GitHub Actions CI](docs/development/github-actions.md)
for what each one runs and how to debug a failure.

Pull requests from forks run with a read-only token and no secrets, so the
SonarQube Cloud gate runs after merge instead. Scanner findings are never
silenced in the same pull request as a code change; accepted exceptions go
through a separate, reviewed policy-only pull request
([gate policy](docs/development/pr-quality-security.md)).

Pull requests are merged with a merge commit so `main` keeps each branch's
history. Clean up fix-up commits before review with
`git rebase -i --autosquash origin/main`.
