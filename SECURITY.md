# Security Policy

## System and Scope

ClinicFlow is an actively developed cloud-native therapy-practice platform
prototype. It contains a Next.js backend-for-frontend, five Spring Boot
services, PostgreSQL databases, RabbitMQ messaging, Docker Compose, Helm
charts, local Kubernetes resources, and Jenkins CI pipelines.

This policy covers:

- the browser-facing Next.js BFF and its `/api/*` routes;
- authentication, JWT issuance, token validation, session cookies, and CSRF
  handling;
- service APIs, ownership checks, persistence adapters, migrations, and event
  consumers/producers;
- RabbitMQ event contracts and payload handling;
- Docker, Helm, Kubernetes, NetworkPolicy, Actuator, and observability
  configuration;
- Jenkins pipelines and repository automation that can affect build or
  release integrity.

The supported deployment scope is local Docker Compose and local Docker Desktop
Kubernetes. ClinicFlow is not production clinical software, is not a medical
device or clinical decision-support system, and must not process real patient
or health data.

Important assets include credentials, JWT key material, access tokens and
session cookies, patient and clinical records, event payloads, logs, database
contents, Kubernetes Secrets, and CI/build credentials.

## Threat Model and Trust Boundaries

The security model treats the following as potentially attacker-controlled:

- browser requests, cookies, headers, query parameters, and request bodies;
- JWTs and other authentication input received by backend services;
- identifiers used to address patients, appointments, clinical cases, and
  notifications;
- RabbitMQ messages at service-consumer boundaries;
- source changes, dependencies, container inputs, and pipeline parameters that
  reach CI automation.

Important trust boundaries are:

1. The browser to the Next.js BFF.
2. The BFF to the Spring Boot service APIs.
3. Each service to its own PostgreSQL database and to RabbitMQ.
4. Kubernetes workloads to platform infrastructure, Secrets, and management
   endpoints.
5. Repository changes to Jenkins build, test, packaging, and image operations.

The local demo credentials, local database credentials, and generated JWT keys
are development conveniences only. They must not be treated as production
credentials or reused outside an isolated local environment.

## Security Invariants

The following properties must hold for supported behavior:

- Protected API operations require valid authentication and appropriate roles.
- Business authorization and ownership checks are enforced in application
  services, not only in frontend visibility checks or optimistic proxy logic.
- A psychologist cannot read or modify another psychologist's patients,
  appointments, clinical records, or scoped notifications.
- JWT signing keys and infrastructure credentials are not committed, exposed in
  logs, or returned in API responses.
- Browser access tokens remain in HttpOnly cookies; mutating same-origin BFF
  requests require the CSRF protection contract.
- Backend errors and BFF responses do not expose stack traces, credentials,
  full tokens, or sensitive clinical payloads.
- Clinical notes and care-plan content are not published through RabbitMQ event
  payloads.
- Each service owns its persistence boundary; cross-service access uses the
  documented API or event contract rather than shared database tables.
- Kubernetes network policies restrict traffic to the documented frontend,
  service, database, broker, DNS, and monitoring flows.
- CI changes must not silently broaden credentials, publish mutable artifacts,
  or deploy an environment without an explicitly approved delivery design.

## Reportable Findings and Severity Context

Please report vulnerabilities that can cause realistic security impact within
the system scope, especially:

- authentication bypass, JWT validation flaws, token leakage, or session
  fixation;
- CSRF bypass or browser-to-BFF authorization weaknesses;
- cross-user or cross-role access to patient, clinical, appointment, or
  notification data;
- injection, unsafe deserialization, SSRF, path traversal, or unsafe handling
  of attacker-controlled event and API input;
- exposure or misuse of private keys, Kubernetes Secrets, database credentials,
  CI credentials, or sensitive logs;
- Kubernetes or container configuration that permits unintended workload,
  network, or management access;
- CI or dependency-integrity weaknesses that can alter trusted build artifacts
  or execute untrusted code with excessive privileges.

Severity is determined by realistic reachability, required privileges, affected
assets, confidentiality or integrity impact, and the likelihood of abuse. A
finding that permits unauthorized access to another user's clinical or health-
related record is high impact even though this repository is currently a local
prototype.

## Reporting a Vulnerability

Please do not publish suspected vulnerabilities, credentials, exploit details,
or real personal data in a public GitHub issue or pull request.

Use GitHub's private vulnerability-reporting mechanism when it is enabled for
the repository. If it is not available, contact the repository owner through a
private GitHub channel and provide only the minimum information needed to
reproduce and assess the issue.

Useful reports include:

- the affected component, file, service, or commit;
- a concise description of the security impact;
- safe reproduction steps using synthetic data only;
- required privileges or deployment assumptions;
- any suggested mitigation or disclosure constraints.

Do not include real patient data, production credentials, private keys, or
unredacted tokens in a report.

## Out of Scope, Exclusions, and Accepted Risk

The following are known product or deployment limitations rather than claims
that the corresponding production capability is complete:

- payments, video calls, real-time chat, document uploads, prescriptions,
  diagnostics, clinical recommendations, emergency systems, and calendar
  integrations;
- production ingress, TLS termination, HSTS, external secret management,
  managed PostgreSQL/RabbitMQ, registry-based artifact promotion, image
  scanning, and SBOM generation;
- fixed demo accounts and local `clinicflow` PostgreSQL/RabbitMQ credentials
  when they remain confined to the documented isolated local setup;
- theoretical issues without a reachable attack path or meaningful impact on a
  supported component.

These limitations must not be used to dismiss an actual authentication,
authorization, data-isolation, secret-exposure, injection, CI-integrity, or
workload-isolation vulnerability.

## Known Limitations and Compensating Controls

- Kubernetes currently targets Docker Desktop and local development rather than
  an internet-facing cluster.
- Local infrastructure uses development credentials and local Secrets. A
  non-local deployment requires managed persistence and externally provisioned
  Secrets.
- TLS, HSTS, production ingress controls, image scanning, and SBOM generation
  remain future hardening work.
- Auth services currently use configured RSA public-key material; dynamic JWKS
  discovery and other identity-provider improvements remain roadmap work.
- RabbitMQ event publication does not yet provide the full reliability
  guarantees of a transactional outbox.
- OWASP ASVS Level 2 is an implementation baseline and evidence map, not a
  certification or compliance claim.

Compensating controls in the current prototype include BCrypt password hashing,
short-lived asymmetric JWTs, HttpOnly BFF cookies, CSRF checks, service-level
ownership authorization, sanitized errors, separate service databases,
structured logging, local-only observability access, and Kubernetes
NetworkPolicies.
