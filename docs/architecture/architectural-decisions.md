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

Decision: implement four domain services: patient, appointment, clinical, and
notification. Identity is not a ClinicFlow service: Keycloak signs users in and
issues tokens (ADR-027).

Rationale: these boundaries match the MVP flows and demonstrate domain CRUD,
scheduling lifecycle events, clinical workflow ownership, and asynchronous event
consumption.

Consequences: each service owns its own persistence model and database. The
original fifth service, `auth-service`, was retired once Keycloak replaced it
(ADR-031).

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

Status: superseded for token issuance by ADR-027. Keycloak signs the tokens and
publishes its keys through JWKS. Validating tokens locally in each resource
server, without a shared signing secret, still holds. This entry describes the
system before the migration (#86 to #90); ADR-031 removed the RSA key pair.

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

Decision: Spring Boot services keep their application ports on 8082 through
8085 (8081 belonged to the retired `auth-service`) and expose Actuator probes and Prometheus metrics on management port 9000
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

Decision: treat the Next.js frontend as a role-aware BFF rather than a generic dashboard. The dashboard layout and sensitive BFF routes verify the Keycloak access token locally and read the profile and role from its claims (ADR-027); login resolves the role-specific home and mutating routes enforce CSRF. Notification records carry the owning psychologist id from domain events through RabbitMQ and persistence.

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
were linked to foreign appointments before this decision release that link to the
appointment's owner (ADR-025). Patient
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

## ADR-025: Appointment Owners Reclaim Conflicting Session-Record Links

Decision: a session record may hold an appointment id only when it belongs to that
appointment's psychologist and patient. Records linked before ADR-021 can break
this rule. Because `session_records.appointment_id` is unique, they block the real
owner's completion record. clinical-service releases such a link when
appointment-service names the owner: either an `appointment.completed` event
arrives, or the owner links the appointment to a manual record and
`GET /appointments/{id}` verifies it. In both cases the service writes a row to
`session_record_link_conflicts` with the record id, the released appointment id, the
appointment's owner, the source (`APPOINTMENT_COMPLETED` or `MANUAL_LINK`) and the
time. Then it sets the old record's `appointment_id` to null and stores the
owner's record in the same transaction. The service logs
`session_record_appointment_conflict ... resolution=link_released`. A record that
already belongs to the owner still answers `409 appointment_already_recorded`, and
a duplicate event is still ignored.

Signed notes: ADR-019 keeps signed rows final, and the conflicting record is often
signed. The `V4` migration narrows the trigger rather than bypassing it. A signed
row accepts one change: an `UPDATE` that clears `appointment_id`, when a
conflict row for that record and appointment already exists. The only other column
that may change is `version`, which must go up by exactly one (ADR-024), so a form
opened on the earlier revision gets `412 stale_resource_version`. Note content,
signature and `updated_at` cannot change in the same statement, and deletes are
still rejected. The domain models this as
`SessionRecord.releaseAppointmentLink`, which returns the audit entry and is the
only change allowed on a signed note.

Rationale: appointment-service is the only authority on appointment ownership
(ADR-004, ADR-021), so the owner's claim wins once that service has confirmed it.
The other record keeps everything its author wrote and signed. It loses only an id
that was never valid, and the audit row keeps that id. Two alternatives were
rejected. Keeping both links would need a non-unique `appointment_id`, which
weakens duplicate protection for every record. Deleting or editing the other
record would break ADR-019.

Existing data: clinical-service cannot find conflicts ahead of time, because
ownership lives in another database. `scripts/maintenance/Find-SessionRecordLinkConflicts.ps1`
reads both databases and lists unresolved, missing and released links. With
`-Replay`, it republishes `appointment.completed` to the clinical queue for
completed appointments whose event was skipped before this decision. See the
[runbook](../infrastructure/session-record-link-conflicts.md).

Consequences: the author of the other record is not notified. Their record keeps
its note but no longer shows an appointment link. The `appointment.completed`
consumer still trusts its producer (ADR-021), so a forged event on the broker could
release a link. Since ADR-026 only appointment-service's broker user and the
administrator may publish `appointment.completed`, and the replay script uses the
administrator login. A database superuser can still bypass the trigger, as ADR-019
notes.

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

## ADR-027: Keycloak as the Identity Provider

Decision: Keycloak 26.8.1 (official image, pinned by digest), with the
`clinicflow` realm defined as code, replaces `auth-service` as the only token
issuer. The Next.js BFF becomes a confidential client using the
authorization-code flow with PKCE. Resource servers validate tokens through
Spring Boot properties only: `jwk-set-uri` on the private network, `issuer-uri`
for the public issuer, the `clinicflow-api` audience, and authorities from a
top-level `roles` claim. Existing user UUIDs stay the token `sub`, because the
realm import and the Admin API `partialImport` keep a user's `id`.
Self-registration yields only `PATIENT`. Kubernetes uses the codecentric
`keycloakx` chart as its own release. See
[ADR-027](decisions/ADR-027-keycloak-identity-provider.md) for the token
contract, the spike evidence, the issuer and backchannel rule, footprint and
upgrade cadence. ADR-027 supersedes ADR-006 for token issuance.

## ADR-028: ClinicFlow PostgreSQL Image without gosu

Decision: PostgreSQL and its bootstrap run ClinicFlow's image built from
`docker/postgres/Dockerfile`: the digest-pinned official image with `gosu`
removed and `USER 70:70`, in Compose, CI and Kubernetes.

Rationale: `gosu` only drops root to UID 70 at container start, and it carried
Go 1.24.6 standard-library CVEs with no upstream rebuild. Starting as UID 70
everywhere makes the binary unnecessary, so it is removed rather than tracked.
See [ADR-028](decisions/ADR-028-postgres-image-without-gosu.md) for the image
tags, upgrade path and consequences.

## ADR-029: Paged List Endpoints

Decision: list endpoints return one bounded page, `{ "content": [...], "page":
{ size, number, totalElements, totalPages } }` (the Spring Data `PagedModel`
shape). `page` counts from 0, and `size` defaults to 20 with a maximum of 100;
there is no unpaged mode. Each resource has one fixed, index-backed order with
the id as tie-breaker, and it filters on the server. Notifications are newest
first, with a repeatable `status` filter. Appointments are in schedule order
(`order=ASC|DESC`), with a half-open `from`/`to` window and a repeatable
`status` filter. Clinical cases are most recently opened first, with `patientId`
and a repeatable `status` filter, and a case's session records are latest
session first. The BFF forwards only allowlisted query parameters. The
dashboard reads its counts from `totalElements` instead of downloading full
lists.

Rationale: every list read loaded the caller's whole history, and appointments,
notifications and session records grow with every session, so response size and
latency grew until they would reach the BFF and gateway timeouts. Offset paging
with an index fits a practice's data volume and gives totals. Keyset paging
stays an option behind the same shape. Patients follow in a later change.
See [ADR-029](decisions/ADR-029-paged-list-endpoints.md) for the contract, the
alternatives considered and what is not paged yet.

## ADR-030: PR Security Gates Block Only What the PR Adds

Decision: PR Trivy and ZAP scans still run in full, but a High/Critical finding
that `main`'s latest scan of the same component and target already had (the
same package version, configuration rule and file, or ZAP alert) is reported as
`inherited` instead of blocking. `main`, nightly and manual runs keep the full
gate. Betterleaks scans only the commits under test.

Rationale: findings already on `main`, such as new upstream CVEs, belong to a
fix on `main`, not to every unrelated PR. The reference comes only from `main`
runs, and without one the gate blocks as before. See
[ADR-030](decisions/ADR-030-pr-scoped-security-gates.md) for the trust rules
and trade-offs.

## ADR-031: Retire auth-service; User Lookup through a Keycloak Service Account

Decision: remove `auth-service` with its database, RSA key pair, gateway routes,
CI pipeline and required check (`clinicflow/auth`). The administrator's user
lookup stays: the BFF reads `GET /admin/realms/clinicflow/users/{id}` and the
account's effective realm roles with a dedicated confidential client,
`clinicflow-user-lookup`, that allows only the client-credentials grant and
holds only `realm-management/view-users`. The BFF checks the administrator role
and a UUID id before calling Keycloak, and maps an account without exactly one
ClinicFlow role to `role: null`.

Rationale: nothing trusted `auth-service` tokens after ADR-027, and a second
user store would drift from the realm. A separate read-only client keeps the
login client's privileges unchanged and makes the Admin API right explicit in
the realm file. See [ADR-031](decisions/ADR-031-auth-service-retirement.md) for
the alternatives, the DAST and required-check changes, and the upgrade steps for
existing environments.

## ADR-032: A ClinicFlow Login Theme for Keycloak

Decision: Keycloak renders its sign-in, registration and other login pages with
a `clinicflow` folder theme that extends `keycloak.v2`. Its layout template adds
the app's brand panel, card eyebrow and page subtitles, and its stylesheet maps
PatternFly 5 onto the frontend's light tokens and self-hosted Instrument fonts.
Page templates, forms and field ids stay upstream's, and the pages stay light.

Rationale: the app's `/login` and `/register` pages and Keycloak's default theme
looked like two products in one sign-in. A layout-level override reaches every
Keycloak page without touching the forms the BFF and the tests drive, and a
repository test keeps the theme's colours equal to the frontend's. See
[ADR-032](decisions/ADR-032-keycloak-login-theme.md) for the alternatives and
the upgrade obligations.
