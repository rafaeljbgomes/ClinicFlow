# ADR-023: Spring Boot 4 platform baseline

Status: implemented. Date: 2026-10-21.

## Context

The services ran on Spring Boot 3.5.16 with Spring Framework 6.2.19, the last
open-source 6.2 release. Two Critical `spring-webmvc` CVEs (CVE-2026-47884,
CVE-2026-47890) are fixed only in Spring Framework 7.0.9, so they had to be
accepted temporarily (issue #33). Boot 3.5 is also the end of the 3.x line.

## Decision

Move every service to Spring Boot 4.1.1, the latest GA release, which brings
Spring Framework 7.0.9, Spring Security 7.1, Spring AMQP 4.1, Spring Data
2026.0, Hibernate 7.4, Flyway 12, Jackson 3.1, Testcontainers 2.0 and JUnit 6.
Java stays on 21.

- **Modular starters.** Services depend on the starters for what they use
  (`webmvc`, `security-oauth2-resource-server`, `flyway`, `amqp`, `data-jpa`)
  and one test starter per technology instead of `spring-boot-starter-test`.
  The classic starters are not used.
- **Jackson 3.** Application code and AMQP conversion use `tools.jackson`
  (`JsonMapper`, `JacksonJsonMessageConverter`). Jackson 3 has no checked
  `JsonProcessingException`; publishers still convert serialization failures
  into the same `IllegalStateException`. Event payloads keep their contract:
  the contract tests compare JSON trees, so Jackson 3's alphabetical property
  order does not change their meaning, and dates stay ISO-8601 strings.
- **Retry without Spring Retry.** Spring AMQP 4 builds listener retry on Spring
  Framework's core retry. `RetryInterceptorBuilder` takes `maxRetries`, which
  excludes the first delivery, so `clinicflow.messaging.retry.max-attempts`
  maps to `maxRetries(maxAttempts - 1)`: still four deliveries before the
  message is dead-lettered.
- **Tests.** API integration tests use Spring Framework 7's `RestTestClient`
  through `@AutoConfigureRestTestClient`, because `TestRestTemplate` is
  deprecated from Boot 4.2. Testcontainers use the 2.0 packages
  (`org.testcontainers.postgresql`, `org.testcontainers.rabbitmq`).
- **Defaults over configuration.** Health liveness and readiness groups are on
  by default, so the explicit probe setting is removed.
- **Security overrides stay explicit.** Tomcat and the RabbitMQ client are
  pinned ahead of Boot's managed versions in the root POM until Boot manages
  fixed releases.

## Consequences

- The `spring-webmvc` exceptions for #33 are obsolete. They are removed in a
  separate policy-only change, as the gate policy requires.
- Code that needs Jackson must use the Jackson 3 API; the Jackson 2
  compatibility module is not on the classpath.
- Changing `max-attempts` keeps its meaning, but any new retry configuration
  must remember that Spring AMQP counts retries, not attempts.
- Mutation testing runs with PIT 1.22 and the JUnit 5 platform plugin on
  JUnit 6; a PIT or plugin release that drops that combination has to be
  checked on upgrade.
- Moving to Java 25, the LTS that Boot 4 recommends, is a separate decision.
