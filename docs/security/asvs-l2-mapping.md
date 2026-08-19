# OWASP ASVS v5.0.0 Level 2 Mapping

ClinicFlow uses OWASP ASVS v5.0.0 Level 2 as a security engineering baseline for
backend APIs. This is not a certification claim; it is an implementation and
evidence matrix for the controls that are meaningful in this prototype phase.

| Area | Implementation decision | Evidence |
| --- | --- | --- |
| Authentication | Passwords are hashed with Spring Security BCrypt. Login responses are generic. | Auth service tests and security configuration. |
| Token/session management | APIs are stateless. Auth service issues short-lived asymmetric JWTs. Resource services validate the public key. | JWT service tests and resource-server integration tests. |
| Browser session handling | The Next.js BFF stores JWTs only in an HttpOnly cookie and uses a separate CSRF cookie/header check for every mutating `/api/*` route, including logout. | Frontend BFF route handlers and local development docs. |
| Access control | RBAC is configured at HTTP boundaries, ownership checks live in application services, dashboard sessions are resolved server-side, and admin BFF endpoints re-check the current role. Notification history is scoped by psychologist ownership. | Security tests for missing, invalid, expired, wrong-role JWTs, and notification repository/controller tests. |
| Input validation | Request DTOs use Bean Validation before data reaches application services. | Controller integration tests. |
| Error handling | A global exception handler returns sanitized problem-style responses. | Error handling tests and manual API smoke tests. |
| Data protection | Services own separate databases. Events and logs minimize personal data. | Migrations, event payloads, and logging tests. |
| Logging | ECS JSON logs include correlation ids and avoid secrets/sensitive payloads. | Logging filter tests and code review. |
| Configuration | Secrets are provided through environment variables or mounted files. Development keys are generated locally and ignored by Git. Kubernetes uses mounted Secrets for JWT material and local-only Secrets for PostgreSQL/RabbitMQ credentials. | Docker Compose, Helm chart values, Kubernetes documentation, and local development docs. |
| API security | CSRF is disabled only for stateless backend APIs, CORS is explicit, Actuator exposure is restricted, and browser mutations go through same-origin BFF CSRF checks. Kubernetes exposes health and Prometheus metrics on an internal management port only. | Security configuration, actuator settings, BFF route handlers, ServiceMonitor resources, and NetworkPolicies. |
| Kubernetes network controls | The platform release declares the namespace default-deny and infrastructure ingress policies; independently managed frontend and service releases own their workload-specific ingress and egress policies. Together they allow only frontend-to-service, service-to-database, service-to-RabbitMQ, DNS, and monitoring-to-metrics traffic. | `deploy/helm/platform/templates/networkpolicy.yaml`, `deploy/helm/service/templates/networkpolicy.yaml`, and `deploy/helm/frontend/templates/networkpolicy.yaml`. |
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
