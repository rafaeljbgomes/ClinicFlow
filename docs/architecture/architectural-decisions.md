# Architectural Decisions

This document records the first implementation decisions for ClinicFlow. The
project is a portfolio-oriented prototype, but the codebase is intentionally
structured so it can evolve into a more serious platform without large
architectural rewrites.

## ADR-001: Monorepo Maven

Decision: use one Maven parent project with one module per microservice.

Rationale: a monorepo keeps local development, dependency alignment, CI, and
documentation simple while still preserving independently deployable Spring Boot
applications.

Consequences: services must not share domain code through a common module.
Small duplication is acceptable when it protects service autonomy.

## ADR-002: Service Boundaries

Decision: implement five services: auth, patient, appointment, clinical, and
notification.

Rationale: these boundaries match the MVP flows and demonstrate authentication,
domain CRUD, scheduling lifecycle events, clinical workflow ownership, and
asynchronous event consumption.

Consequences: each service owns its own persistence model and database.

## ADR-003: Clean Architecture per Service

Decision: each service is split into domain, application, adapters, and
infrastructure packages.

Rationale: the domain stays independent from Spring, JPA, RabbitMQ, HTTP, and
database concerns. Application ports keep dependencies pointing inward.

Consequences: JPA entities, controllers, DTOs, message publishers, and security
configuration remain outside the domain layer.

## ADR-004: PostgreSQL Database per Service

Decision: each service uses its own PostgreSQL database and Flyway migrations.

Rationale: data ownership is a core microservices boundary. Flyway keeps local,
CI, and future deployed environments reproducible.

Consequences: cross-service reads are not implemented through shared tables.

## ADR-005: RabbitMQ for Asynchronous Events

Decision: patient and appointment services publish domain events to a RabbitMQ
topic exchange. Notification service consumes notification-worthy patient and
appointment events, while clinical service consumes appointment completion for
session-record creation.

Rationale: this demonstrates event-driven communication without introducing a
full workflow orchestrator in the first phase.

Consequences: event payloads are minimal, carry correlation ids, and avoid
clinical details. Clinical notes and care-plan content are never published over
RabbitMQ.

## ADR-006: Asymmetric JWT

Decision: auth-service signs JWTs with a private RSA key; other services validate
tokens with the public key through Spring Security Resource Server.

Rationale: this avoids sharing a signing secret across services and is a better
security baseline for a system handling personal and health-related data.

Consequences: local development requires generating development RSA keys.

## ADR-007: Structured Logging

Decision: all services use Spring Boot structured ECS JSON logs and propagate a
correlation id through HTTP and RabbitMQ.

Rationale: structured logs make investigation, future aggregation, and
observability work easier while keeping the prototype production-minded.

Consequences: logs must not include passwords, full JWTs, or sensitive health
payloads.

## ADR-008: OWASP ASVS Level 2 Baseline

Decision: use OWASP ASVS v5.0.0 Level 2 as the backend API security baseline.

Rationale: ClinicFlow handles personal and potentially health-sensitive data.
Even as a prototype, the implementation should avoid security shortcuts that
would be expensive to unwind later.

Consequences: the codebase includes an ASVS mapping and security tests for the
most relevant backend controls.

## ADR-009: Defer CQRS and Redis

Decision: do not implement CQRS or Redis in phase 1.

Rationale: they are useful future additions, especially for read-heavy
appointment workflows, but they would add premature complexity before the
microservices foundation is stable.

Consequences: phase 3 may introduce internal CQRS in appointment-service first,
then Redis as a cache-aside or reconstructible read model. PostgreSQL remains
the source of truth.

## ADR-010: Next.js BFF instead of Vite SPA

Decision: implement the first frontend as a Next.js App Router application with
route handlers acting as a lightweight backend-for-frontend.

Rationale: a Vite SPA would require browser-to-service cross-origin calls and
would push token handling into the browser runtime. The Next.js BFF lets the
browser call same-origin `/api/*` endpoints, keeps JWTs in HttpOnly cookies, and
centralizes CSRF checks, correlation id forwarding, and backend error
normalization.

Consequences: the frontend is deployed as its own service on port 3000 and
requires service URL environment variables. Route handlers forward bearer tokens
to the Spring Boot APIs and must not expose raw JWTs, stack traces, or sensitive
backend payloads to the UI.

## ADR-011: Helm for Application and Observability Packaging

Decision: package the Kubernetes phase with Helm charts: one chart for the
ClinicFlow application and one observability wrapper around kube-prometheus-stack.

Rationale: Helm keeps the local Kubernetes deployment repeatable while exposing
the operational resources that matter for learning: Deployments, Services,
StatefulSets, Secrets, ConfigMaps, probes, NetworkPolicies, ServiceMonitors, and
Grafana dashboards.

Consequences: Docker Compose remains the fast local development path, while Helm
is the canonical Kubernetes path. The observability chart requires
`helm dependency update` before installation because kube-prometheus-stack is a
remote chart dependency.

## ADR-012: Docker Desktop Kubernetes as the Local Cluster Target

Decision: use Docker Desktop Kubernetes as the primary local Kubernetes target
for this phase.

Rationale: the project already depends on Docker Desktop-style local workflows,
and this target lets locally built images be used by the cluster without a
registry or image-loading step.

Consequences: the Kubernetes manifests are local-first. The documentation marks
PostgreSQL and RabbitMQ StatefulSets as didactic local infrastructure, not a
production persistence strategy.

## ADR-013: kube-prometheus-stack and Prometheus Operator

Decision: install Prometheus and Grafana through kube-prometheus-stack and
discover ClinicFlow metrics with Prometheus Operator ServiceMonitor resources.

Rationale: ServiceMonitor is a Kubernetes-native scraping contract and better
matches real platform practice than hand-written Prometheus scrape config.
Grafana dashboards are provisioned from version-controlled ConfigMaps.

Consequences: the application chart depends on the monitoring CRDs being
installed first. Prometheus and Grafana are exposed locally through
`kubectl port-forward`, not through NodePort or Ingress.

## ADR-014: Separate Management Port for Internal Metrics

Decision: Spring Boot services keep their application ports on 8081 through
8085 and expose Actuator probes and Prometheus metrics on management port 9000
when the `kubernetes` profile is active.

Rationale: separating operational endpoints from API traffic makes it easier to
limit access with Services, ServiceMonitors, and NetworkPolicies while keeping
the frontend API URLs unchanged.

Consequences: `/actuator/health/**`, `/actuator/info`, and
`/actuator/prometheus` are unauthenticated, but only the health endpoints are
exposed in the default profile. Other Actuator endpoints remain role-protected.

## ADR-015: Domain Identity, Value Objects, and Boundary Mappers

Decision: model domain identities and validated concepts as immutable value
objects inside each service, while keeping aggregate entities identity-based and
mutable through explicit business methods. Use MapStruct-backed mapper classes
at REST, application result, JPA, and RabbitMQ boundaries.

Rationale: entities have a lifecycle and must preserve their identity while
their state changes. Value objects have no lifecycle, compare by value, and own
normalization and validation rules. Dedicated mappers prevent transport and
persistence conversion logic from leaking into entities, use cases, and
repositories.

Consequences: entity mutation methods validate before changing state and return
the same entity instance. Value-object accessors return equivalent defensive
copies. Repository save ports return `void`, so persistence cannot replace an
in-memory aggregate. Each bounded context defines its own value-object types;
there is no shared domain module. HTTP and RabbitMQ payload shapes remain scalar
and stable even when the internal domain uses richer types. Patient phone
numbers require international E.164 format and are validated with
libphonenumber.

## ADR-016: Role-Aware Frontend and Notification Ownership

Decision: treat the Next.js frontend as a role-aware BFF rather than a generic dashboard. The dashboard layout validates the session with the auth service, login resolves the role-specific home, mutating routes enforce CSRF, and sensitive BFF routes perform an explicit role check. Notification records carry the owning psychologist id from domain events through RabbitMQ and persistence.

Rationale: psychologists need a quiet clinical workspace, administrators need operational controls, and patient accounts must not fall through into clinician screens. A notification list shared by all psychologists would leak practice activity even if the visible UI hid technical fields.

Consequences: psychologists only query notifications owned by their JWT user id; administrators retain the global delivery view. The BFF further projects psychologist messages to practice-safe fields. Legacy notification rows may have a null owner and remain visible only to administrators. Proxy checks remain optimistic and are never the authorization boundary.
