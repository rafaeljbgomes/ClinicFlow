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

Decision: package the Kubernetes phase with independently managed Helm releases:
one shared platform chart, one reusable Spring-service chart installed once per
backend service, one frontend chart, and one observability wrapper around
kube-prometheus-stack.

Rationale: Helm keeps the local Kubernetes deployment repeatable while exposing
the operational resources that matter for learning: Deployments, Services,
StatefulSets, Secrets, ConfigMaps, probes, NetworkPolicies, ServiceMonitors, and
Grafana dashboards.

Consequences: Docker Compose remains the fast local development path, while Helm
is the canonical Kubernetes path. Application releases can be upgraded and
rolled back independently; PostgreSQL, RabbitMQ, platform policy, and shared
dashboards have a separate ownership boundary. The observability chart requires
`helm dependency update` before installation because kube-prometheus-stack is a
remote chart dependency. See ADR-017 and
[DM-01](delivery-modernization/01-release-packaging.md).

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

Consequences: application and platform charts render ServiceMonitor resources
and therefore depend on the monitoring CRDs being installed first for live
installation. Prometheus and Grafana are exposed locally through
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

Decision: treat the Next.js frontend as a role-aware BFF rather than a generic dashboard. The dashboard layout obtains the full user profile from auth-service, while sensitive BFF routes verify RS256 JWT claims locally for their role check; login resolves the role-specific home and mutating routes enforce CSRF. Notification records carry the owning psychologist id from domain events through RabbitMQ and persistence.

Rationale: psychologists need a quiet clinical workspace, administrators need operational controls, and patient accounts must not fall through into clinician screens. A notification list shared by all psychologists would leak practice activity even if the visible UI hid technical fields.

Consequences: psychologists only query notifications owned by their JWT user id; administrators retain the global delivery view. The BFF further projects psychologist messages to practice-safe fields. Legacy notification rows may have a null owner and remain visible only to administrators. BFF role checks are a defense-in-depth optimization; Spring Security remains the authorization boundary.

## ADR-017: Independent Service Delivery with Shared Platform Capabilities

Decision: make each deployable application component independently buildable,
releasable, deployable, observable, and rollbackable. Keep Jenkins,
Kubernetes, observability, RabbitMQ, and the PostgreSQL hosting layer as shared
platform capabilities, with explicit service-level access and ownership
boundaries.

Rationale: at decision time, ClinicFlow had separate application containers and
Kubernetes Deployments, but its local workflow built all images and used one
Helm release with one application-wide image tag. That made service
revisions, promotion, and rollback coupled even where the business boundaries
are separate. Requiring a separate Jenkins controller, cluster, or physical
server per service would add operational cost without improving the delivery
property that matters: a team must be able to change one service without
releasing the rest.

Consequences: delivery modernization is divided into the isolated work packages
in `docs/architecture/delivery-modernization/`. The existing root Jenkins
pipeline remains a system-validation pipeline rather than being deleted. A
future service pipeline must publish an immutable image, deploy one service
release, verify that release, and roll back only that release. Platform and
cross-service changes remain subject to a system integration gate. DM-01 is
tracked by [GitHub issue #1](https://github.com/rafaeljbgomes/ClinicFlow/issues/1).

## ADR-018: Traefik Gateway and Kubernetes Edge

Decision: use one Traefik edge with separate UI/API hostnames, a private BFF listener,
Gateway API in Kubernetes, and file-provider routes in Compose. Business/JWT ownership
remains in each service; cookies, CSRF, roles, and projection remain in Next.js.

The independently managed controller and application gateway releases preserve ADR-017
service delivery. See [ADR-018](decisions/ADR-018-traefik-gateway.md) for the route contract,
CRD lifecycle, TLS, local quotas, telemetry, and acceptance limitations. ADR-018 supersedes
ADR-013's direct UI exposure and ADR-014's unchanged frontend service URLs.

## ADR-019: Signed Session Notes Are Final

Decision: signing a session note is its own action (`POST /session-records/{id}/sign`), allowed
only for the treating psychologist on a `DRAFT` note with a summary. It records `signedAt` and
`signedBy`. Edits through `PATCH /session-records/{id}` only move notes between `PENDING_NOTE`
and `DRAFT`. They return `400 invalid_note_transition` for `SIGNED` and `409 session_record_signed`
for a signed note. Signing an incomplete note returns `409 session_record_not_signable`.

The domain enforces the lock, and PostgreSQL backs it (`V2__lock_signed_session_records.sql`). A
CHECK constraint ties the signature columns to `note_status`. A row trigger rejects `DELETE` and
any changing `UPDATE` of a signed row with SQLSTATE `23L01`, which the persistence adapter maps to
the same `409`. Since ADR-024 the version check rejects a stale edit or signature first; the
trigger remains the backstop for code paths that bypass versions or `update()`.

Consequences: demo seeding inserts session records only when absent. Corrections need linked
amendment records, which are future work; until then a signed note cannot be changed. A database
superuser can still disable triggers, and `TRUNCATE` does not fire them.

## ADR-020: Transactional Outbox for Producer Reliability

Decision: patient-service and appointment-service persist outbound domain events
to a service-local PostgreSQL outbox in the same transaction as their aggregate
changes. Only a scheduled relay may select and publish outbox records; request
transactions never publish directly to RabbitMQ.

The relay claims one due `PENDING` or `FAILED` record per short transaction
with `FOR UPDATE SKIP LOCKED`, allowing multiple service replicas to poll
without concurrently publishing the same claimed row. It sends the stored JSON
payload without re-encoding it, restores the original event id and correlation
id as message metadata, and marks the record `PUBLISHED` only after a correlated
publisher acknowledgement with no mandatory return.

Rationale: an aggregate write and a direct broker send cannot be committed
atomically. The outbox makes the database transaction the durable handoff:
serialization or persistence failure rolls back both changes, while RabbitMQ
unavailability does not fail an otherwise valid API transaction. Publisher
confirms and mandatory returns distinguish broker acceptance from negative
acknowledgements and unroutable messages.

Consequences: delivery is at least once, not exactly once. A broker acknowledgement
followed by a database status-update failure can cause the relay to publish the
same event again. The original event id remains stable across retries, and the
existing notification and clinical persistence uniqueness constraints suppress
duplicate effects.

Failed records remain retryable indefinitely with exponential backoff from one
second to one minute. Each polling cycle processes at most 20 records.
`PUBLISHED` records are retained for seven days and removed by an hourly cleanup;
pending and failed records are never removed by retention cleanup. Backlog and
failure metrics plus structured event-id, routing-key, retry-count, and outcome
logs make the relay observable. This implementation is intentionally duplicated
inside the two bounded services instead of creating a shared runtime module.

## ADR-021: Owning Services Verify Cross-Service References

Decision: a service that stores another service's identifier on behalf of a
psychologist first asks the owning service whether the caller owns it, using the
caller's own bearer token. appointment-service and clinical-service call
`HEAD /patients/{id}` on patient-service before scheduling an appointment or
opening a clinical case. clinical-service calls `GET /appointments/{id}` on
appointment-service before a manual session record links an appointment, and
accepts the link only when the appointment belongs to the case's patient and has
no session record yet.

Rationale: patient ownership lives only in patient-service (ADR-004), and the API
hostname reaches services directly, so BFF checks cannot protect it. Before this
decision a psychologist could attach appointments and clinical cases to another
psychologist's patient, and a manual session record could claim another
psychologist's appointment id. Because `session_records.appointment_id` is unique,
that claim silently stopped the real owner's completed appointment from creating a
session record. Forwarding the caller's token keeps patient-service and
appointment-service as the only authorities. It needs no service credentials or
replicated ownership data. `HEAD` returns only a status, so no patient details reach
the calling service. An event-fed ownership copy was rejected: demo patients are
seeded without events, so it would need backfill and reconciliation, and it would
reject newly created patients until the event arrived.

Contract: `2xx` means owned. `403` and `404` both become `404 patient_not_found`
or `404 appointment_not_found`, so callers cannot probe for other practices'
identifiers. Any other status, a timeout, a transport error, or a caller token that
does not belong to the checked psychologist fails closed with
`503 ownership_check_unavailable`. A linked appointment of another patient returns
`400 appointment_patient_mismatch`, and an appointment that already has a session
record returns `409 appointment_already_recorded`. The ownership check runs before
the local duplicate check, so a foreign appointment id reveals nothing. Clients use
one-second connect and two-second read timeouts, never follow redirects, and
forward the correlation id.

Identifier probing: every endpoint that addresses a patient, appointment, clinical
case, or session record by id answers another psychologist's resource exactly like
a missing one. The status, problem code, title, and detail match, for example
`404 patient_not_found` or `404 clinical_resource_not_found`. The denial is
written to the service log (`patient_access_denied`, `appointment_access_denied`,
`clinical_case_access_denied`, `session_record_access_denied`) with the resource
and caller ids, so it stays auditable without reaching the caller. `403
access_denied` remains only for role checks and for rules inside the caller's own
records, such as a care-goal id outside the plan, whose answer does not depend on
whether the id exists. Response timing is not normalised. Notifications already
followed this rule (ADR-016).

Consequences: scheduling and case creation now depend on patient-service at
request time, and linking a session record depends on appointment-service. Reads
and the other lifecycle operations do not. The check runs inside the write
transaction, so a slow neighbour holds a database connection for up to the read
timeout. Helm declares the new service-to-service NetworkPolicy flows per release
(`networkPolicy.serviceIngress` and `serviceEgress`), and Compose sets the base URLs.
The `appointment.completed` consumer still trusts its producer, because
appointment-service now verifies patients at scheduling time. Session records that
were linked to foreign appointments before this decision are not rewritten; the
consumer logs `session_record_appointment_conflict` when it meets one. Patient
status (for example `ARCHIVED`) is not part of the check.

## ADR-022: GitHub Actions as the PR Gate of Record

Decision: pull requests, `main`, nightly and manual validation run on
GitHub-hosted runners. One workflow per deployable component plus `system` and
`repository` call the existing `scripts/ci` entry points, route changes with the
shared `security-policy.cjs` rules, and always report a final
`clinicflow/<component>` check that branch rules can require. PIT runs on
`main`, nightly and on demand rather than on PRs. Jenkins remains an on-demand
local runner. See [ADR-022](decisions/ADR-022-github-actions-ci.md) for the
trade-offs, including how workflow changes are debugged before they are merged.

## ADR-023: Spring Boot 4 Platform Baseline

Decision: the services run on Spring Boot 4.1 with Spring Framework 7, Spring
Security 7, Spring AMQP 4, Hibernate 7, Jackson 3, Testcontainers 2 and JUnit 6,
on Java 21. They use the modular starters and per-technology test starters,
Jackson 3 for application and message JSON, Spring Framework's core retry for
listener retry (four deliveries before dead-lettering, unchanged), and
`RestTestClient` for API integration tests. See
[ADR-023](decisions/ADR-023-spring-boot-4.md) for the migration details and
consequences.

## ADR-024: Optimistic Concurrency for Mutable Records

Decision: patients, appointments, clinical cases, care plans, session records and
practice profiles carry a persistence-assigned version (`@Version`); a care plan's
version covers its goals. Every update and action names the version it was based
on with `If-Match: "<version>"`. A missing header returns `428
precondition_required`, a malformed one `400 invalid_precondition`, and a stale
version `412 stale_resource_version` with nothing changed. Responses carry the
version in the body and, for single records, as `ETag`. Care plans and practice
profiles are created by their first `PUT` with `If-None-Match: *`. The version is
compared after the ADR-021 ownership check, so foreign ids still answer `404`.

Rationale: writes replaced whole rows from the loaded snapshot, so a stale form
silently reverted consent, deleted care goals, overwrote clinical text or signed
a revision nobody reviewed, and racing appointment commands left contradictory
outbox events. The UI sends the version of the snapshot a form opened with and,
on `412`, keeps the user's input and offers to reload the latest version instead
of merging. See [ADR-024](decisions/ADR-024-optimistic-concurrency.md) for the
contract, the alternatives considered and the consequences.

## ADR-026: Per-Service Database and Broker Credentials

Decision: each service has its own PostgreSQL owner role (used only by Flyway), a
runtime role with data-only privileges on its own database, and, for messaging
services, its own RabbitMQ user whose permissions name exactly the exchanges,
queues and routing keys it uses, so it can neither forge another service's
events nor subscribe to events it does not consume. `CONNECT` is revoked from `PUBLIC`, so no service reaches
another service's database. The administrator logins stay with the platform.
Passwords are generated into Git-ignored files. An idempotent bootstrap
(Compose services, Helm hook Job) creates or rotates the identities, and
post-start hooks re-apply the administrator passwords to existing volumes. See
[ADR-026](decisions/ADR-026-per-service-credentials.md) for the privilege table,
rotation and residual risks.
