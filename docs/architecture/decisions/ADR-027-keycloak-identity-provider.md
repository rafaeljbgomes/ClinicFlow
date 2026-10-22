# ADR-027: Keycloak as the identity provider

Status: accepted; implementation tracked in #86 to #90. Date: 2026-10-22.
Issue: #85. Epic: #91. Supersedes ADR-006 for token issuance.

## Context

`auth-service` is a hand-written identity provider. It checks the password
itself (BCrypt, cost 12) and signs a 30-minute RS256 access token with a key
loaded at startup. It has no refresh token, logout, revocation, brute-force
protection, MFA or key rotation. A new `kid` is generated on every start and
every consumer pins the PEM public key instead of reading a JWKS.

Today's token carries `iss: clinicflow-auth`, `sub` and a duplicate `userId`
(both the user UUID), `email`, and a single-string `role`. The consumers depend
on it as follows:

- Patient, appointment, clinical and notification services each carry
  `PemUtils`, `JwtProperties`, `JwtRoleConverter` and a custom `JwtDecoder`.
  They take the caller id from `getClaimAsString("userId")` and store it as an
  owner id (for example `psychologistId`). The ADR-021 ownership checks compare
  against it. `NotificationController` also reads the `role` claim directly.
- The Next.js BFF (ADR-010) posts the user's email and password to
  `/auth/login`, so it collects passwords and trades them for a token. With an
  OAuth provider that is the resource-owner password grant, which RFC 9700
  forbids. `frontend/src/lib/server/jwt.ts` verifies against the pinned PEM and
  requires `userId`, `role` and `sub == userId`.
- `POST /auth/register` lets any caller choose `PSYCHOLOGIST`.
  `RegisterUserService` only downgrades `ADMIN`.

This ADR records the throwaway spike that tested the risky assumptions, and the
resulting decisions. The spike is in
[`docs/architecture/spikes/keycloak-identity`](../spikes/keycloak-identity/README.md)
and can be re-run with one command.

## Decision

1. **Keycloak is the only token issuer.** Use the official
   `quay.io/keycloak/keycloak` image, version 26.8.1, pinned by digest. The
   `clinicflow` realm is defined as code in the repository and nobody edits it
   in the admin console.
2. **The BFF is a confidential OIDC client** (`clinicflow-bff`). It uses the
   authorization-code flow with PKCE `S256` and has direct access grants
   disabled. Tokens stay server-side in HttpOnly cookies, as ADR-010 requires.
   The BFF never sees a password.
3. **Resource servers validate tokens through Spring Boot properties only.**
   The caller is `sub`, roles come from a top-level `roles` claim, and the
   audience is checked. The copied security code is removed, not moved into a
   shared library.
4. **The token contract, the identity-continuity rule, the issuer rule and the
   packaging below are binding** for #86 to #90.

### Token contract

The access token is what the BFF forwards to the services. It is a JWS signed
with RS256, and its `kid` resolves in the realm JWKS.

| Claim | Value | Consumers | Rule |
|---|---|---|---|
| `iss` | `<public Keycloak URL>/realms/clinicflow` | BFF, services | Exactly one value per environment (see [Issuer](#issuer-hostnames-and-backchannel)). Must match exactly. |
| `sub` | User UUID (string) | BFF, services | The caller id. Stored owner ids are `sub` values. |
| `aud` | `clinicflow-api` | BFF, services | Must contain `clinicflow-api`. Comes from an audience mapper. |
| `roles` | JSON array with exactly one of `ADMIN`, `PSYCHOLOGIST`, `PATIENT` | BFF, services | Top-level claim from a realm-role mapper with claim name `roles`. Consumers check membership and ignore unknown values. |
| `email` | Login email | BFF (display) | Informational. Never used as an identifier. |
| `exp`, `iat` | Epoch seconds | BFF, services | Checked with the library's default clock skew. |
| `azp` | `clinicflow-bff` | None | Informational. |
| `email_verified`, `name`, `given_name`, `family_name`, `preferred_username`, `scope`, `sid`, `jti`, `typ`, `auth_time` | Keycloak defaults | None required | No consumer may depend on them. `name` replaces `fullName` for display if the BFF wants it. |

The following no longer exist and no consumer may read them: `userId`, `role`
(string), `realm_access`, `resource_access`.

The ID token is for the BFF only. It has `aud: clinicflow-bff`, carries the
`nonce` and has no `roles`.

How the realm produces the contract (verified in the spike):

- The client's default client scopes are `basic` (which emits `sub` since
  Keycloak 25), `email` and `profile`. The built-in `roles`, `web-origins` and
  `acr` scopes are not attached, so `realm_access` and extra audiences never
  appear.
- `fullScopeAllowed` is `false`. The client's role scope mapping is exactly
  `ADMIN`, `PSYCHOLOGIST` and `PATIENT`. Keycloak's own roles
  (`default-roles-clinicflow`, `offline_access`, `uma_authorization`) are
  effective for every self-registered user but are cut out of the token. The
  spike's self-registered user had all of them and received
  `roles: ["PATIENT"]`.
- Lightweight access tokens stay disabled, because `sub` must be in the access
  token.
- Lifetimes: access token 5 minutes, SSO idle 30 minutes (today's TTL), SSO max
  10 hours. Refresh tokens rotate: `revokeRefreshToken` is on with zero reuse,
  and the spike saw a reused refresh token rejected with `invalid_grant`.

Example: the decoded access token for the seeded psychologist, from the spike.
It was issued by a code exchange on the private network, and its signature was
verified against the JWKS.

```json
{
  "header": { "alg": "RS256", "typ": "JWT", "kid": "Gdi1fggQCGoW6LzBmCOQcYnwjmleFrWoNtwVAEuO_iE" },
  "payload": {
    "exp": 1792692617,
    "iat": 1792692317,
    "auth_time": 1792692315,
    "jti": "onrtac:378c05ee-ba35-c3b9-3279-a7501f9d5daf",
    "iss": "http://localhost:8180/realms/clinicflow",
    "aud": "clinicflow-api",
    "sub": "00000000-0000-4000-8000-000000000011",
    "typ": "Bearer",
    "azp": "clinicflow-bff",
    "sid": "itliajO6UwmaYKhBciYSDJJX",
    "scope": "openid profile email",
    "email_verified": true,
    "roles": ["PSYCHOLOGIST"],
    "name": "Sofia Almeida",
    "preferred_username": "sofia.almeida@demo.clinicflow.local",
    "given_name": "Sofia",
    "family_name": "Almeida",
    "email": "sofia.almeida@demo.clinicflow.local"
  }
}
```

### Identity continuity

Rule: **a user who already exists in `auth-service` keeps their UUID as the
Keycloak user `id`, and therefore as `sub`.** Stored owner ids and the ADR-021
checks keep working without a data migration.

What the spike showed on 26.8.1:

| Path | `id` kept? | Notes |
|---|---|---|
| Realm import (`--import-realm`, or the realm file in general) | Yes | All five demo UUIDs came back as `sub`. Users get exactly the `realmRoles` listed and no default role. |
| Admin REST `POST /admin/realms/clinicflow/partialImport` | Yes | Same behaviour as the realm import. Use this for any user added to a running realm. |
| Admin REST `POST /admin/realms/clinicflow/users` | **No** | The `id` in the body is ignored and a random UUID is assigned. The user also gets the default role. Never use it to recreate an existing user. |
| Self-registration | Not applicable | Keycloak assigns a random v4 UUID. The user owns no data yet. |

Consequences:

- The demo users move into the realm file (#86) with the UUIDs from
  `DemoDataBootstrap`, so seeded patients, appointments and notes keep their
  owners.
- Any other `auth-service` accounts are synthetic local data and are not
  migrated by default. If one is needed, export `id`, `email`, `role` and name
  from `clinicflow_auth.users` and load them through `partialImport` with an
  `UPDATE_PASSWORD` required action. BCrypt hashes are not carried over. That
  would need a custom password-hash provider in Keycloak, which is not worth it
  for synthetic data.
- The fallback the issue named (a `userId` user attribute plus mapper, or an id
  migration in the owning services) is **not needed and is rejected**. It would
  keep two identifiers alive.
- Services and the BFF switch from the `userId` claim to `sub` (#87, #88). All
  ids stay RFC 4122 v4-shaped, so the BFF's UUID pattern still holds. The demo
  ids use version nibble `4` and variant `8`.

### Roles and self-registration

- Realm roles `ADMIN`, `PSYCHOLOGIST` and `PATIENT`. The default-role composite
  `default-roles-clinicflow` contains `PATIENT`, plus the `offline_access` and
  `uma_authorization` roles that Keycloak adds itself. Self-registration
  therefore yields `PATIENT` and nothing else, which the spike verified.
- `PSYCHOLOGIST` and `ADMIN` are granted only by an administrator. Because every
  user created in a running realm gets the default composite, provisioning a
  psychologist means assigning `PSYCHOLOGIST` **and** removing
  `default-roles-clinicflow`. The alternative is `partialImport` with explicit
  `realmRoles`, which does not add the default. Otherwise the token would carry
  two ClinicFlow roles and break the "exactly one" rule. #86 documents the
  procedure.
- **Product consequence.** Today the public "Create account" page registers a
  psychologist workspace. After #88 it creates a `PATIENT` account, which lands
  on the access-limited page. Psychologist onboarding becomes an administrator
  task. This is the epic's intended invariant and closes the privilege path in
  `RegisterUserService`. The login copy and docs must say so.

### Issuer, hostnames and backchannel

Every party sees one issuer per environment. Keycloak's `KC_HOSTNAME` is set to
the full public URL, and `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true` lets callers on
the private network use the internal address:

| Environment | Public URL (`KC_HOSTNAME`; the issuer is this plus `/realms/clinicflow`) | Private-network URL |
|---|---|---|
| Compose (local and CI) | `http://localhost:8180` | `http://keycloak:8080` |
| Compose HTTPS acceptance and Docker Desktop Kubernetes behind Traefik (ADR-018) | `https://auth.clinicflow.localhost` | `http://keycloak:8080` (Compose) or the in-cluster Service (Kubernetes) |

The spike verified this split. Discovery fetched from `http://keycloak:8080`
returns the public `issuer`, `authorization_endpoint` and
`end_session_endpoint`, and the internal `token_endpoint` and `jwks_uri`. A
code exchanged at the internal token endpoint, and the refreshed token, both
carry the public `iss`. The authorization response also carries the RFC 9207
`iss` parameter with the public issuer.

The public URL must be HTTPS or a `localhost` host. Keycloak marks its
login-session cookies `Secure; SameSite=None` even over HTTP, and browsers only
send those to secure contexts (`https`, `http://localhost`,
`http://*.localhost`). So an issuer such as `http://host.docker.internal` or a
LAN IP would break login.

**Services use `jwk-set-uri` plus `issuer-uri`, not `issuer-uri` alone.** In
Spring Boot 4.1.1, when `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`
is set, `IssuerUriCondition` turns off the discovery-based decoder. The decoder
is built from the JWKS, which is fetched lazily on the first token rather than
at startup, and `issuer-uri` only adds a `JwtIssuerValidator` (see
`JwtDecoderConfiguration`). `issuer-uri` alone would make each service fetch
discovery from the public URL, which is not reachable from inside a container
in Compose. It would also tie service startup to Keycloak being up. #87
configures:

```yaml
spring.security.oauth2.resourceserver.jwt:
  issuer-uri: ${CLINICFLOW_OIDC_ISSUER}          # public, compared with iss
  jwk-set-uri: ${CLINICFLOW_OIDC_JWKS_URI}       # private network
  audiences: [clinicflow-api]
  authorities-claim-name: roles
  authority-prefix: ROLE_
  # principal-claim-name stays unset, so the principal name is sub
```

Two code details for #87. Each `SecurityConfig` passes
`new JwtRoleConverter()` to `oauth2ResourceServer`, which would bypass the
auto-configured `JwtAuthenticationConverter`. It must use
`jwt(Customizer.withDefaults())` instead. `NotificationController.isAdmin`
must check the `ROLE_ADMIN` authority instead of the `role` claim.

**The BFF** sends the browser to the public authorization and end-session
endpoints. It exchanges and refreshes codes at the internal token endpoint and
verifies tokens with a remote JWKS from the internal `jwks_uri`, checking the
public `iss` and `aud: clinicflow-api`. OpenID Connect Discovery requires the
returned `issuer` to equal the URL discovery was fetched from. So the BFF
either configures these endpoints explicitly, or discovers through the public
issuer with requests rerouted to the internal host. Fetching discovery from
`http://keycloak:8080` and validating the result fails, by design. #88 picks
one.

**The gateway (#89)** routes only `/realms/` and `/resources/` on
`auth.clinicflow.localhost`. The admin console is not routed and stays on a
local port or `kubectl port-forward`. The existing login rate limit (10/minute,
burst 5) moves to the Keycloak login-action paths. Keycloak's brute-force
detection (5 failures, then an incremental wait up to 15 minutes) is the
per-account control behind it.

### Packaging and runtime

- **Image.** A ClinicFlow image built `FROM` the official image, which runs
  `kc.sh build` with `KC_DB=postgres`, `KC_HEALTH_ENABLED=true`,
  `KC_METRICS_ENABLED=true` and `KC_CACHE=local`, then `start --optimized`.
  Plain `start` re-runs the Quarkus build on every boot. `KC_CACHE=local` is
  right for one replica. The default clustered cache starts JGroups
  (`JDBC_PING` discovery) for no benefit.
- **Health.** Readiness is `GET /health/ready` on management port 9000, judged
  by the **HTTP status**. The spike's first health check grepped the body for
  `"UP"` and passed while Keycloak was still `DOWN`, because the body lists
  sub-checks that are `UP`. Requests that arrive during bootstrap get `503`.
- **Realm import is create-only** in both packaging options. `--import-realm`
  logged `Realm 'clinicflow' already exists. Import skipped` on the second
  start, and the Operator's `KeycloakRealmImport` documents the same rule. Realm
  changes after the first start therefore need a realm reset in development
  (drop the Keycloak volume) or an idempotent applier such as
  `keycloak-config-cli`. #86 decides which. CI always starts from empty
  volumes, so it always exercises the committed file.
- **Secrets.** The realm file holds `${CLINICFLOW_BFF_CLIENT_SECRET}` and
  password placeholders, which Keycloak resolves from the environment at import.
  The spike confirmed this for the client secret and the user credentials. No
  secret is committed. The bootstrap admin, database password and client secret
  come from generated, Git-ignored files, following ADR-026.
- **Kubernetes: the codecentric `keycloakx` chart** (7.4.0, `appVersion`
  26.8.1) with the ClinicFlow image, installed as its own Helm release (ADR-011,
  ADR-017). The realm is mounted from the same JSON file that Compose uses, and
  placeholders come from Secrets. Alternatives considered:
  - **Keycloak Operator** (official). It would add a second always-on JVM pod
    and two cluster-scoped CRDs (platform-owned, as in ADR-018), installed from
    raw manifests rather than Helm. The realm would have to be restated as a
    `KeycloakRealmImport` resource, and that import is also create-only, so the
    Operator gives nothing extra for realm-as-code on a single-replica local
    cluster. Revisit it if ClinicFlow ever needs managed upgrades or HA.
  - **Bitnami chart.** Rejected. Its images went behind a subscription in 2025.
  - **A first-party template in the `platform` chart**, as for PostgreSQL and
    RabbitMQ. Possible, but `keycloakx` already provides the StatefulSet,
    probes, management port and import wiring, and keeps Keycloak a separate
    release.

  The chart is community-maintained, not shipped by the Keycloak project. Its
  releases have tracked Keycloak closely: 7.4.0 shipped 26.8.1 one day after
  it. Pin the chart version and the image digest, as the gateway work does.

### Footprint

Measured on 2026-10-22 on Docker Desktop (Docker 29.8.2, 16 CPUs, a VM with
7.44 GiB) with `Run-Spike.ps1`: Keycloak 26.8.1 plus PostgreSQL 16.15, five
users, and four logins or registrations per run. "Ready" means the first HTTP
200 from `/health/ready`, timed from `docker compose up`.

| Run | Cold start, empty DB, realm import | Warm restart | Keycloak memory, idle after cold start | After logins |
|---|---|---|---|---|
| Optimized image, limit 1 GiB | 42.2 s | 13.2 s | 601 MiB | 576 MiB |
| Optimized image, limit 768 MiB | 42.1 s | 11.1 s | 572 MiB | 530 MiB |
| Upstream image with plain `start`, limit 1 GiB | 54.1 s | 11.2 s¹ | 712 MiB | 559 MiB |
| Optimized image, no limit (first run) | 46.7 s | 13.2 s | 1.81 GiB | 736 MiB |

¹ A restart reuses the container, so it also reuses the build that plain `start`
wrote into it. A new container or pod pays the roughly 12-second rebuild again.

Keycloak's database used 52 to 59 MiB.

- Quarkus reports "started" after about 7 s. Most of a cold start is the
  one-time creation of the Keycloak schema plus the realm import. Earlier logs
  showed the schema changelog taking about 30 s. Later starts skip it.
- Without a memory limit the JVM sizes its heap from the VM (70 % of RAM by
  default) and grew to 1.8 GiB. **Always set a limit.** 768 MiB was enough for
  this workload. Kubernetes (#89) should start at request 512Mi and limit
  768Mi, with a CPU limit of at least 1 core, because startup is CPU-bound.
  Give the startup probe at least 90 s for the first install.
- On the local cluster, Keycloak replaces `auth-service` and its database.
  `scripts/scale-local-kubernetes-to-zero.ps1` scales every Deployment and
  StatefulSet in the application and monitoring namespaces, so a `keycloakx`
  StatefulSet in the `clinicflow` namespace is covered with no change. If #89
  installs it in its own namespace, the script needs that namespace added. The
  script is not on `main` yet; it exists only as an untracked file in a working
  copy.
- Not measured: the memory of today's `auth-service`, so the net change on the
  cluster is not quantified here. #89 records it.

### Version and upgrade cadence

Pin `26.8.1` (released 2026-10-13) by digest, in the Dockerfile and the chart
values. Keycloak supports only its latest release. Fixes, including security
fixes, ship as patches of the current minor only, and there is no LTS stream.
Minors have arrived roughly quarterly (26.6.0 on 2026-04-08, 26.7.0 on
2026-07-09, 26.8.0 on 2026-10-01), with patches every two to three weeks. Rule:
take every patch within two weeks of release, and move to a new minor once its
first patch is out, after reading the upgrading guide. Dependabot's Docker
ecosystem must watch the Keycloak Dockerfile (#86).

## Consequences

- ADR-006 is superseded for **issuance**: Keycloak signs the tokens and
  publishes its keys through JWKS. Validating tokens locally in each resource
  server, without sharing a signing secret, still holds.
- Refresh, RP-initiated logout, brute-force protection, a password policy and
  key rotation come from Keycloak. Rotation works only because consumers read
  the JWKS instead of pinning a key.
- The login and registration pages are Keycloak's (themed or default, #88).
  #79 (registration email enumeration) has to be re-evaluated against
  Keycloak's registration form.
- The admin user lookup (`/users/{id}`) loses its backend when `auth-service`
  goes away. #90 decides between a BFF call to the Admin API with a dedicated
  service-account client, or removal.
- Psychologist accounts are provisioned by an administrator (see above).
- The local stack gains one JVM process and one database, in place of
  `auth-service` and its database.
- The ASVS mapping (ADR-008) changes owner for authentication controls (V6/V7):
  password storage, lockout and session lifetime are Keycloak settings
  committed in the realm file, so they need to be checked against the baseline
  in #86 instead of in Java tests.

## Primary references

- Keycloak 26.8 server guides: hostname v2, health, caching, importing and
  exporting realms; Operator realm-import guide.
- Spring Boot 4.1.1, `spring-boot-security-oauth2-resource-server`:
  `JwtDecoderConfiguration`, `IssuerUriCondition`, `JwtConverterConfiguration`.
- RFC 9700 (OAuth 2.0 Security BCP), RFC 7636 (PKCE), RFC 9207 (authorization
  response `iss`), OpenID Connect Discovery 1.0 §4.3.
