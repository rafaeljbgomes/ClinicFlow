# ADR-031: Retire auth-service; admin user lookup through a Keycloak service account

Status: implemented. Date: 2026-10-30. Extends ADR-027. Amends ADR-002 and
ADR-022.

## Context

Since #87, #88 and #89, Keycloak issues every token that the resource servers,
the BFF and the gateway accept (ADR-027). `auth-service` still ran in Compose,
CI and Kubernetes, but nothing trusted its tokens. It kept a database
(`clinicflow_auth`), an RSA key pair (`secrets/jwt-*.pem`, the `clinicflow-jwt`
Secret), three gateway routes, a CI pipeline with its own required check
(`clinicflow/auth`), and a DAST role whose only purpose was to scan it.

One feature still depended on it. The administrator's system page has a
**user access lookup**: paste a user UUID, see the account's name, email, role
and whether it is enabled. `auth-service` answered it from its own `users`
table. Since #88 the BFF route has answered `501`, because `auth-service` does
not accept Keycloak tokens and its table no longer reflects the realm. ADR-027
left the choice to #90: call the Keycloak Admin API from the BFF with a
dedicated service-account client, or remove the feature.

Profile reads (`/users/me`) already come from the BFF's verified access token,
so they need no replacement.

## Decision

### 1. auth-service is removed everywhere

The Maven module, its image, Compose service, Helm values file, Jenkinsfile,
GitHub workflow, gateway routes and backends, NetworkPolicy peers, database,
service identity, JWT key pair and `clinicflow-jwt` Secret are deleted. The
platform has four domain services plus Keycloak. Keycloak's realm already holds
the demo users with their original UUIDs (ADR-027), so `DemoDataBootstrap`
has no successor.

### 2. The user lookup stays, backed by the Keycloak Admin API

The BFF route `GET /api/users/{id}` keeps its contract (`UserView`) and calls
the Admin API on Keycloak's private address with a token for a new,
dedicated client:

| Property | Value |
| --- | --- |
| Client | `clinicflow-user-lookup`, confidential, secret generated into `secrets/keycloak/clinicflow-user-lookup.env` (ADR-026) |
| Flows | Client credentials only. Standard, implicit, direct-grant and device flows are off. |
| Privilege | Client role `realm-management/view-users` on the service-account user, with a matching client scope mapping and `fullScopeAllowed: false`. |
| Token | No `clinicflow-api` audience and no `roles` claim, so the resource servers and the BFF session check reject it. |
| Calls | `GET /admin/realms/clinicflow/users/{id}` and `GET .../users/{id}/role-mappings/realm/composite`. Nothing else. |

The route:

1. verifies the caller's own access token and requires `ADMIN` (as the health
   route does), before any call to Keycloak;
2. accepts only an RFC 4122 UUID, so the id cannot reshape the Admin API path;
3. gets a client-credentials token from the private token endpoint and caches
   it in memory until 30 seconds before it expires;
4. maps the two Admin API responses to `{ id, email, fullName, role, enabled }`.
   `role` is the single ClinicFlow realm role among the effective (composite)
   mappings, or `null` when the account has none or several. Such an account
   cannot sign in to the BFF (ADR-027, "exactly one role"), so showing `null`
   tells the administrator why;
5. answers `404 user_not_found` for an unknown id, `400 invalid_user_id` for a
   malformed one, and `503 user_lookup_unavailable` when Keycloak or the
   client secret is missing. Keycloak's error bodies are never forwarded.

### 3. Required checks drop from eight to seven

`clinicflow/auth` is removed from `.ci/security.json` and its workflow is
deleted. The `main_protection` ruleset must drop the same context before this
change merges, or the merge waits for a check that no longer runs.
`scripts/ci/Set-RequiredQualityChecks.ps1 -VerifiedCommit <head> -Apply`
applies the new list after verifying that all seven passed on that commit.

### 4. DAST scans what exists

The always-scanned `auth-service` component and its bearer-token rule are
removed. Each role's authenticated coverage is the BFF's `/api/auth/me` plus
the manifest endpoints that role may call. A role with no API endpoint in
scope (the patient, or the administrator when notifications are not in scope)
is covered through the BFF only. The administrator's coverage adds the new
user lookup route. The demo password comes from `CLINICFLOW_DEMO_PASSWORD`,
which the realm import already uses, instead of being parsed from
`DemoDataBootstrap.java`.

## Alternatives considered

- **Remove the lookup.** Simplest and the smallest attack surface. Rejected
  because the administrator would lose the only in-app way to see whether an
  account is enabled and which role it holds, and the Keycloak admin console
  is deliberately not routed (ADR-027). The service account keeps the feature
  at read-only privilege.
- **Reuse the BFF login client with service accounts enabled.** No new secret,
  but the client that handles user logins would also hold Admin API rights,
  and both privileges would rotate together. A separate client keeps the
  login client's scope unchanged and makes the privilege visible in the realm
  file.
- **Fine-grained admin permissions (FGAP v2)** to restrict the service account
  to viewing users without the list/search that `view-users` implies. It needs
  a feature flag, permission policies in the realm export and more contract
  tests for a one-tenant prototype. `view-users` is the narrowest built-in
  role that allows reading one user by id.
- **Keep `auth-service` as a read-only user directory.** It would have to be
  synchronised with Keycloak, which is the dual source of truth ADR-027
  rejected.

## Consequences

- The local stack runs one JVM fewer. ADR-027 measured `auth-service` at about
  250 MiB on the cluster.
- The BFF holds a second Keycloak secret. A BFF compromise could read the
  realm's user directory (names, emails, roles, enabled flags) through the
  service account, but could not change anything: `view-users` grants no
  write, credential or client access. That directory is synthetic
  (ADR-027 invariants).
- Admin API reads are not admin events, so Keycloak does not log the lookups.
  The BFF logs nothing either; the route's caller is already an
  authenticated administrator.
- Existing realms do not have the new client, because Keycloak imports the
  realm only once. `scripts/reset-keycloak-realm.ps1` (Compose and
  `-Target Kubernetes`) applies it; the deploy script already warns when the
  realm file changed since import. Until then the lookup answers `503`.
- Existing environments keep `auth-service` artefacts until cleaned up:
  `docs/development/local-development.md` and
  `docs/infrastructure/kubernetes-observability.md` give the commands, and
  `scripts/deploy-local-kubernetes.ps1` warns while the old release exists.
- SonarCloud keeps the `auth-service` project's history; it receives no new
  analyses.
- Historical ADRs (ADR-006, ADR-016, ADR-021, ADR-026, ADR-027) still name
  `auth-service` where they describe past decisions. ADR-002 now lists four
  domain services.
