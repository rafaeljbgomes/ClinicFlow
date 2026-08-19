# Testing Strategy

## Goals

The backend test suite protects business rules, authorization and ownership
checks, HTTP contracts, persistence mappings, Flyway migrations, event
publication and RabbitMQ topology. Tests are kept close to the unit or
integration they describe so failures identify a narrow responsibility.

## Suite Types

- `*Test.java` contains unit tests or Spring MVC slice tests and runs with
  Surefire during `mvn test`.
- `*IT.java` contains repository, messaging and functional HTTP integration tests and runs with
  Failsafe during `mvn verify`.
- Domain entities, value objects, application services, mappers, filters,
  security components, controllers and exception handlers each have dedicated
  test classes.
- Repository integrations run against PostgreSQL Testcontainers with Flyway
  enabled and Hibernate schema validation.
- Messaging integrations run against RabbitMQ Testcontainers. Unit tests mock
  `RabbitTemplate` or application ports when broker behaviour is outside the
  test boundary.
- Full-context smoke tests replace only JWT decoding or token issuing while
  retaining real Spring wiring, PostgreSQL and RabbitMQ.
- `*ApiIT.java` starts the real HTTP server on a random port and exercises the
  controller, security filter chain, application service, mapper, Flyway and
  PostgreSQL. JWT decoding and outbound event publication are replaced only
  when those systems are outside the boundary of the scenario.
- Messaging end-to-end tests retain RabbitMQ, the real listener, application
  service and PostgreSQL, and use Awaitility for eventual assertions.
- Canonical event examples live in `event-contracts`. Producer tests must
  serialize to those examples and consumer tests must deserialize and map the
  same resources. The module currently contracts `patient.created` and all
  four appointment lifecycle events.

## Test doubles

- Pure unit tests use Mockito for application output ports and captors for
  commands/events whose content is part of the behaviour.
- Spring slices use `@MockitoBean` only for collaborators outside the slice.
- Repository, functional and messaging tests use real persistence mappings and
  infrastructure inside their declared boundary.
- Do not mock domain entities or value objects, use deep stubs, or verify
  implementation calls that are not observable behaviour. Dummies are limited
  to mandatory values that a scenario intentionally does not inspect.

## Commands

Fast feedback without Docker:

```powershell
mvn test
```

Complete backend verification with Docker running:

```powershell
mvn clean verify
```

The clean phase prevents deleted test sources from surviving as orphaned
classes under `target/test-classes`. Maven Clean Plugin 3.5 runs with forced
deletion so Windows workspaces synchronized by OneDrive can remove read-only
build artifacts reliably.

Mutation testing for domain and application logic:

```powershell
mvn -pl event-contracts -am -DskipTests install
mvn -Pmutation org.pitest:pitest-maven:mutationCoverage
```

Run a single module:

```powershell
mvn -pl patient-service test
mvn -pl patient-service -am clean verify
```

Run a specific test:

```powershell
mvn -pl auth-service -Dtest=LoginServiceTest test
mvn -pl patient-service -Dit.test=JpaPatientRepositoryIT verify
```

## Coverage

JaCoCo merges unit and integration execution data during `verify`.

Quality gates:

- At least 85% line coverage per service.
- At least 80% branch coverage per service.
- At least 90% line and 85% branch coverage for domain and application packages.
- At least 90% PIT line coverage and 75% mutation score for behavioural domain
  and application code. Commands, queries, results, mappings and exceptions are
  excluded because they are data carriers or separately verified mappings.

Application bootstrap classes, generated REST DTOs and generated MapStruct
implementations are excluded from the gate. Generated mapper behaviour is still
covered through mapper contract tests.

Reports:

- Per-service HTML: `<service>/target/site/jacoco/index.html`
- Per-service XML: `<service>/target/site/jacoco/jacoco.xml`
- Aggregate HTML: `coverage-report/target/site/jacoco-aggregate/index.html`
- Aggregate XML: `coverage-report/target/site/jacoco-aggregate/jacoco.xml`
- Mutation HTML/XML: `<service>/target/pit-reports/`

The XML reports are suitable for CI coverage publishing. The HTML reports are
intended for local investigation of uncovered lines and branches.

## Adding Tests

1. Add or update unit tests for changed business rules and failure paths.
2. Add a controller slice test when an HTTP contract, validation or role rule
   changes.
3. Add a repository or messaging integration test when a migration, mapping,
   query, event payload or routing key changes.
4. Keep external systems outside the tested boundary mocked unless their
   protocol or configuration is the purpose of the integration test.
5. Run `mvn test` during development and `mvn clean verify` before merging.

## Continuous integration

The root `Jenkinsfile` is the local CI entry point. It runs on the dedicated
`clinicflow-ci` agent and publishes no images or deployments. The same gates can
be run directly from the repository root with PowerShell 7:

```powershell
.\scripts\ci\Test-RepositoryHygiene.ps1
.\scripts\ci\Invoke-BackendFast.ps1
.\scripts\ci\Invoke-Frontend.ps1
.\scripts\ci\Test-Platform.ps1
.\scripts\ci\Invoke-BackendFull.ps1
.\scripts\ci\Build-Images.ps1
.\scripts\ci\Invoke-ComposePlaywright.ps1 -RunId local-full
```

The Jenkins `FAST` level runs repository hygiene, backend-fast, frontend
quality, and platform validation. `FULL` adds backend integration verification,
image builds, and isolated Compose/Playwright validation. If an isolated run is
interrupted, clean up only that run:

```powershell
.\scripts\ci\Stop-IsolatedCompose.ps1 -RunId local-full
```

The Compose-backed validation uses fresh named volumes and an isolated project
name, so it does not target the developer's normal Compose environment. The
Maven Wrapper and the committed frontend lockfile remain the authoritative
build inputs for clean validation.
