# OWASP ASVS v5.0.0 Level 2 Mapping

ClinicFlow uses OWASP ASVS v5.0.0 Level 2 as a security engineering baseline for
backend APIs. This is not a certification claim; it is an implementation and
evidence matrix for the controls that are meaningful in this prototype phase.

| Area | Implementation decision | Evidence |
| --- | --- | --- |
| Authentication | Passwords are hashed with Spring Security BCrypt. Login responses are generic. The Keycloak realm that replaces `auth-service` (ADR-027) sets the same controls in configuration: Argon2 hashing, 12 to 128 characters, no username or email as the password, brute-force detection (5 failures, then an incremental wait up to 15 minutes), 5-minute access tokens, a 30-minute idle and 10-hour maximum session, and refresh tokens that rotate on every use. Self-registration grants only `PATIENT`. | Auth service tests and security configuration; `docker/keycloak/realm/clinicflow-realm.json`, checked against a running stack by `scripts/check-keycloak-realm.py`. |
| Token/session management | APIs are stateless. Keycloak issues 5-minute RS256 access tokens (ADR-027). Patient, appointment, clinical and notification services validate them with Spring Boot resource-server properties only: keys from the realm JWKS, which allows key rotation, an exact `iss`, the `clinicflow-api` audience and expiry. Roles come from the top-level `roles` claim, and the caller is `sub`. The BFF verifies the same token against the same JWKS, and requires exactly one ClinicFlow role. | `TokenValidationTest` in each service (wrong issuer, wrong audience, expired, foreign signature, missing roles, `realm_access`-only roles) against a stand-in realm JWKS; the BFF's `jwt.test.ts`; JWT service tests for auth-service. |
| Browser session handling | The Next.js BFF is a confidential OIDC client using the authorization-code flow with PKCE `S256`, so it never handles a password. The callback checks `state`, the RFC 9207 `iss` parameter and the ID token's `nonce` and audience. Access and refresh tokens live only in HttpOnly, `SameSite=Lax` cookies, Secure in deployed environments, and never appear in a URL. The BFF refreshes them before expiry with one shared exchange per refresh token, and ends the session when Keycloak refuses a refresh. Sign-out ends the Keycloak session on the back channel. A separate CSRF cookie/header check covers every mutating `/api/*` route, including logout. | `oidc.test.ts`, `proxy.test.ts` and `auth-routes.test.ts` in the frontend; Playwright sign-in, refresh, sign-out and cookie tests against the Compose stack; the DAST cookie checks. |
| Access control | RBAC is configured at HTTP boundaries, ownership checks live in application services, dashboard sessions are resolved server-side, and admin BFF endpoints re-check the current role. Notification history is scoped by psychologist ownership. Patient and appointment references are confirmed with their owning service using the caller's token, and the check fails closed. Another psychologist's resource answers exactly like a missing one, so identifiers cannot be probed (ADR-021). | Security tests for missing, invalid, expired, wrong-role JWTs, notification repository/controller tests, two-owner `AppointmentApiIT`/`ClinicalApiIT` scenarios against real HTTP stand-ins, and foreign-versus-missing response comparisons in the patient, appointment, and clinical API tests. |
| Input validation | Request DTOs use Bean Validation before data reaches application services. | Controller integration tests. |
| Error handling | A global exception handler returns sanitized problem-style responses. | Error handling tests and manual API smoke tests. |
| Data protection | Services own separate databases and connect with their own least-privilege logins: Flyway uses an owner role, the application a data-only runtime role, and each messaging service a RabbitMQ user limited to its own exchanges, queues and routing keys, so it can neither forge another service's events nor subscribe to events it does not consume (ADR-026). Events and logs minimize personal data. | Migrations, event payloads, logging tests, and the idempotent platform bootstrap scripts. |
| Logging | ECS JSON logs include correlation ids and avoid secrets/sensitive payloads. | Logging filter tests and code review. |
| Configuration | Secrets are provided through environment variables or mounted files. Development keys are generated locally and ignored by Git. Database and broker passwords are generated per service and per platform administrator, never committed, and applied or rotated by an idempotent bootstrap. Kubernetes mounts the JWT Secret only into auth-service and the BFF, and uses one credentials Secret per service. | Docker Compose, Helm chart values, Kubernetes documentation, and local development docs. |
| API security | CSRF is disabled only for stateless backend APIs, CORS is explicit, Actuator exposure is restricted, and browser mutations go through same-origin BFF CSRF checks. Kubernetes exposes health and Prometheus metrics on an internal management port only. | Security configuration, actuator settings, BFF route handlers, ServiceMonitor resources, and NetworkPolicies. |
| Kubernetes network controls | The platform release declares the namespace default-deny and infrastructure ingress policies; independently managed frontend and service releases own their workload-specific ingress and egress policies. Together they allow only frontend-to-service, service-to-database, service-to-RabbitMQ, the declared ownership checks (appointment and clinical to patient, clinical to appointment), DNS, and monitoring-to-metrics traffic. | `deploy/helm/platform/templates/networkpolicy.yaml`, `deploy/helm/service/templates/networkpolicy.yaml`, and `deploy/helm/frontend/templates/networkpolicy.yaml`. |
| Observability access | Prometheus and Grafana run inside the monitoring namespace and are accessed locally through `kubectl port-forward`, not public Ingress or NodePort. | `deploy/helm/observability/values-docker-desktop.yaml` and Kubernetes observability docs. |
| Dependency hygiene | Maven dependency management is centralized by Spring Boot parent and Testcontainers BOM. | `mvn test` and future CI dependency checks. |

## Deferred to Infrastructure Phase

- TLS termination, HSTS, and ingress security headers.
- Container image scanning and SBOM generation in CI.

## Remaining Future Hardening

- TLS termination, HSTS, and ingress security headers for a non-local cluster.
- External secret management, such as Sealed Secrets, External Secrets Operator,
  or a cloud KMS-backed secret provider.
- Managed PostgreSQL/RabbitMQ or dedicated operators for production-grade
  persistence and messaging.
- Container image scanning and SBOM generation in CI.
