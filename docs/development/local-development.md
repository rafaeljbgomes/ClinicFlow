# Local Development

## Prerequisites

- Java 21
- Maven Wrapper 3.9.16 (the repository wrapper is authoritative)
- Node.js 24.19.0 and npm 11 for host-side frontend work
- Docker Desktop or Docker Engine
- OpenSSL available in the shell path

## Generate Development Credentials

Each service has its own PostgreSQL and RabbitMQ logins, and the platform
administrator logins are never given to a service (ADR-026). Generate the
local-only passwords before running Docker Compose:

```powershell
.\scripts\generate-dev-credentials.ps1
```

The script writes `secrets/platform/*.env` (administrators),
`secrets/services/<service>.env` (per service) and `secrets/keycloak/*.env`
(Keycloak's database login, its bootstrap administrator, the BFF client secret
and the user lookup client secret). All of them are ignored by Git. It keeps existing files, so it is safe
to rerun. To rotate every password, run it with `-Rotate` and then
`docker compose up -d`. The bootstrap services apply the new service passwords,
and PostgreSQL and RabbitMQ re-apply their administrator password when they
start, so existing volumes keep their data. Keycloak reads its client secrets
only when it imports the realm, and its bootstrap administrator only when its
database is new, so also run `./scripts/reset-keycloak-realm.ps1 -Full` after a
rotation (see [Keycloak](#keycloak)). On Kubernetes, run
`./scripts/deploy-local-kubernetes.ps1` after `-Rotate`, then
`./scripts/reset-keycloak-realm.ps1 -Target Kubernetes -Full`. The deploy
re-runs the platform bootstrap with the new passwords and rolls every workload
whose Secret changed, the BFF included.

To run a service from an IDE against the Compose infrastructure, load the
variables from its `secrets/services/<service>.env`.

## Run Tests

Run the fast unit and Spring MVC slice suites:

```powershell
.\mvnw.cmd test
```

Run the complete backend quality gate, including integration tests and coverage:

```powershell
.\mvnw.cmd verify
```

Integration tests use Testcontainers with PostgreSQL and RabbitMQ and therefore
require Docker. `verify` also generates per-service and aggregate JaCoCo reports
and fails when the configured coverage thresholds are not met.

See [Testing Strategy](testing-strategy.md) for suite conventions, isolation
rules, coverage thresholds, and report locations.

## Run Locally with Docker Compose

```powershell
./scripts/generate-dev-credentials.ps1
./scripts/gateway/New-DevelopmentCertificate.ps1
docker compose up --build
```

Docker Compose includes two one-shot bootstrap services that run before the
Spring Boot services. `postgres-bootstrap` creates any missing service databases
in existing PostgreSQL volumes and clears incompatible legacy `patients.phone`
values, so the strict E.164 Flyway migration can be applied locally. It then
creates or updates each service's owner role (used by Flyway) and runtime role
(data access only), and hands objects created under the old shared login to the
owner role. `rabbitmq-bootstrap` declares the shared exchanges and creates or
updates each messaging service's RabbitMQ user, its permissions, and the routing
keys it may publish and bind with. A new event or subscription therefore needs
its routing key added to `deploy/helm/platform/files/bootstrap/service-identities.conf`.
A bootstrap fails with a pointer to the
generator when credentials are missing.

Docker Compose also enables the demo-data bootstrap through
`CLINICFLOW_DEMO_BOOTSTRAP_ENABLED=true`. On service startup, each backend seeds
its own database with deterministic demonstration data for the local UI. The
bootstrap uses fixed demo identifiers and overwrites the same demo records on
subsequent starts without deleting non-demo data.

Demo credentials:

- Admin: `admin@demo.clinicflow.local`
- Psychologist: `sofia.almeida@demo.clinicflow.local`
- Psychologist: `miguel.santos@demo.clinicflow.local`
- Patient: `ana.martins@demo.clinicflow.local`
- Patient: `joao.ferreira@demo.clinicflow.local`
- Password for all demo accounts: `ClinicFlowDemo!2026`

The demo dataset includes patients, consent/contact variants, appointments in
all lifecycle states, practice profiles, clinical cases, care plans, care goals,
session records, and notification history. Disable it by removing or setting
`CLINICFLOW_DEMO_BOOTSTRAP_ENABLED=false` for each backend service.

Public endpoints:

- UI/BFF: `https://clinicflow.localhost`.
- Bearer API: `https://api.clinicflow.localhost/api/v1/**`.
- RabbitMQ Management: `http://localhost:15672`.

Backend/frontend application ports and management 9000 are unpublished. Trust the generated
local CA manually in the browser; see [gateway setup and acceptance](../infrastructure/gateway.md).

RabbitMQ credentials for local development are `clinicflow` / `clinicflow`.

## Keycloak

Keycloak 26.8.1 is ClinicFlow's identity provider (ADR-027). The patient,
appointment, clinical and notification services accept only Keycloak access
tokens (see [Resource servers](#resource-servers)), and the BFF signs users in
through Keycloak (see [BFF sign-in](#bff-sign-in)). Keycloak replaced
`auth-service`, which was retired in ADR-031.

| | |
| --- | --- |
| Public URL and issuer | `http://localhost:8180`, issuer `http://localhost:8180/realms/clinicflow` |
| Private-network URL | `http://keycloak:8080` (tokens, keys and discovery for containers) |
| Admin console | `http://localhost:8180/admin`, bound to 127.0.0.1 only |
| Console login | The `master` realm's bootstrap administrator from `secrets/keycloak/admin.env` |
| Database | `clinicflow_keycloak` on the ClinicFlow PostgreSQL, owned by the `clinicflow_keycloak` login from `secrets/keycloak/database.env` |

On Docker Desktop Kubernetes, Keycloak runs as the `clinicflow-keycloak`
release behind the gateway (see
[Run Locally with Docker Desktop Kubernetes](#run-locally-with-docker-desktop-kubernetes)):

| | |
| --- | --- |
| Public URL and issuer | `https://auth.clinicflow.localhost`, issuer `https://auth.clinicflow.localhost/realms/clinicflow` |
| Private-network URL | `http://keycloak:8080`, the `keycloak` Service |
| Admin console | No gateway route. `kubectl port-forward svc/keycloak 8180:8080 -n clinicflow`, then `http://localhost:8180/admin/` |

The bootstrap administrator is Keycloak's own administrator, not a ClinicFlow
user. `admin@demo.clinicflow.local` is a ClinicFlow user with the `ADMIN` role
and cannot open the console.

### Realm as code

The `clinicflow` realm is defined only in
[`docker/keycloak/realm/clinicflow-realm.json`](../../docker/keycloak/realm/clinicflow-realm.json),
which is built into the `clinicflow/keycloak` image. Do not change the realm in
the admin console: those changes are lost on the next reset and never reach CI.
Secrets are `${...}` placeholders that Keycloak resolves from the environment
when it imports the realm, so the file holds no client secret or password.

Keycloak imports the realm only when it does not exist yet. The first
`docker compose up` imports it, and later starts log
`Realm 'clinicflow' already exists. Import skipped`, so restarting is safe and
keeps the users. After you change the realm file, apply it with:

```powershell
./scripts/reset-keycloak-realm.ps1
```

The script rebuilds the image, then deletes the `clinicflow` realm and imports
it again. Anything that is not in the file is lost, such as self-registered
users, and the demo users come back with their fixed ids. `-Full` drops
Keycloak's whole database instead, including the `master` realm, so the next
start also applies a rotated bootstrap administrator. Both leave the other
services' data alone. CI always starts from empty volumes, so it always tests
the committed file.

On Kubernetes, `scripts/deploy-local-kubernetes.ps1` warns when the realm file
has changed since Keycloak imported it, and never resets on its own. Apply the
change with:

```powershell
./scripts/reset-keycloak-realm.ps1 -Target Kubernetes
```

It rebuilds the image, scales Keycloak to zero, runs the import in a one-off pod
with Keycloak's own configuration and network policy, and starts Keycloak again.
`-Full` drops the database through the PostgreSQL pod and re-runs the platform
bootstrap instead.

An idempotent applier such as `keycloak-config-cli` was considered and not
adopted. It would add a second image and JVM run to every `docker compose up`
for a realm that only changes through reviewed commits, and the reset gives the
same end state for a local, synthetic-data stack.

### Demo accounts and checks

The realm holds the demo users that `auth-service` used to seed, with the same
ids, roles and synthetic password, so the seeded patients, appointments and notes keep
their owners. Each has exactly one ClinicFlow role and no default role.

To check a running stack against the ADR-027 token contract, run:

```powershell
py -I scripts/check-keycloak-realm.py
```

The script signs in as every demo user with the authorization-code flow and
PKCE, verifies each access token's signature, `iss`, `sub`, `aud` and `roles`,
and checks refresh-token rotation and that the BFF client is refused the
password grant. It also registers a throwaway user, confirms it gets only
`PATIENT`, and deletes it again.

### Resource servers

The four business services validate access tokens with Spring Boot's
resource-server properties only (`spring.security.oauth2.resourceserver.jwt.*`).
There is no token-decoding or key-loading code in them. Two variables locate the
realm:

| Variable | Compose | Default for a service run on the host |
| --- | --- | --- |
| `CLINICFLOW_OIDC_ISSUER` | `http://localhost:${CLINICFLOW_KEYCLOAK_PORT:-8180}/realms/clinicflow` | `http://localhost:8180/realms/clinicflow` |
| `CLINICFLOW_OIDC_JWKS_URI` | `http://keycloak:8080/realms/clinicflow/protocol/openid-connect/certs` | `http://localhost:8180/realms/clinicflow/protocol/openid-connect/certs` |

The issuer is the public one, because that is the `iss` Keycloak puts in every
token, while the keys come from the private-network JWKS. With `jwk-set-uri` set,
Spring Boot does not fetch discovery, and it reads the JWKS on the first
authenticated request rather than at startup, so the services start without
waiting for Keycloak. Each `application.yml` fixes the rest of the contract:
audience `clinicflow-api`, authorities from the top-level `roles` claim with the
`ROLE_` prefix, and the caller id from `sub`. A token with another `iss` or
`aud`, an expired token or a bad signature gets `401`. A valid token without a
ClinicFlow role gets `403`. Roles under `realm_access` are ignored.

`TokenValidationTest` in each service runs the decoder that Spring Boot builds
from these properties against a stand-in realm JWKS, with tokens in the ADR-027
shape. To check by hand against the running stack, get a demo user's access
token with the authorization-code flow that `scripts/check-keycloak-realm.py`
uses, and call a service on the private network from the gateway container:

```powershell
docker compose exec gateway wget -S -q -O - `
  --header "Authorization: Bearer $token" http://patient-service:8082/patients
```

The same call with an ID token, a token signed by any other key or a token older
than five minutes answers `401`.

### BFF sign-in

The BFF is the realm's confidential `clinicflow-bff` client and uses the
authorization-code flow with PKCE `S256`. "Sign in" on `/login` goes to
`/api/auth/login`, which keeps `state`, `nonce` and the PKCE verifier in a
ten-minute HttpOnly cookie scoped to `/api/auth/callback` and sends the browser
to Keycloak's login page. The callback checks `state`, the RFC 9207 `iss`
parameter and the ID token's `nonce` and audience, exchanges the code on the
private network with the client secret, and sets three cookies:

| Cookie | HttpOnly | Lifetime |
| --- | --- | --- |
| `clinicflow_access_token` | Yes | The access token's 5 minutes |
| `clinicflow_refresh_token` | Yes | The Keycloak session (30 minutes idle) |
| `clinicflow_csrf` | No, the browser client sends it back as `x-csrf-token` | The Keycloak session |

The ID token is checked and discarded. `proxy.ts` refreshes the tokens when the
access token is within 30 seconds of expiry, and hands the new ones to the same
request. Concurrent requests share one refresh, because Keycloak rotates refresh
tokens and refuses a reused one. A refused refresh ends the session. Sign-out
ends the Keycloak session on the back channel with the refresh token, clears
the cookies and sends the browser to Keycloak's end-session page, which returns
to `/login`. No token appears in a URL.

The BFF needs these variables:

| Variable | Compose |
| --- | --- |
| `CLINICFLOW_OIDC_ISSUER` | `http://localhost:${CLINICFLOW_KEYCLOAK_PORT:-8180}/realms/clinicflow` |
| `CLINICFLOW_OIDC_INTERNAL_ISSUER` | `http://keycloak:8080/realms/clinicflow` (token, keys, back-channel logout) |
| `CLINICFLOW_PUBLIC_URL` | `${CLINICFLOW_PUBLIC_URL:-https://clinicflow.localhost}`; `/api/auth/callback` on it must be a redirect URI of the client |
| `CLINICFLOW_BFF_CLIENT_SECRET` | From `secrets/keycloak/clinicflow-bff.env` |

The sign-in starts on the public URL's host. A request on another host, such as
`127.0.0.1` instead of `localhost`, is first redirected there, because the
transaction cookie has to be on the origin Keycloak returns to. Keycloak's own
login and registration pages use its default theme.

### Administrator user lookup

The System page's user lookup reads one account from Keycloak's Admin API
(ADR-031). The BFF route `GET /api/users/{id}` checks that the caller is an
administrator and that the id is a UUID, then calls
`http://keycloak:8080/admin/realms/clinicflow/users/{id}` and the account's
effective realm roles with a token for the `clinicflow-user-lookup` client:

| Variable | Compose |
| --- | --- |
| `CLINICFLOW_USER_LOOKUP_CLIENT_SECRET` | From `secrets/keycloak/clinicflow-user-lookup.env` |
| `CLINICFLOW_USER_LOOKUP_CLIENT_ID` | Optional, `clinicflow-user-lookup` by default |

The client allows only the client-credentials grant, and its service account
holds `realm-management/view-users` and nothing else, so it can read users but
not change them, read clients or events, or reach another realm. Its token has
no `clinicflow-api` audience and no `roles` claim, so no resource server and no
BFF route accepts it. An account with no ClinicFlow role, or with several,
shows "No role": the BFF would refuse its sign-in.

The route answers `404` for an unknown id and `503` when Keycloak is down or
the secret is missing. A realm imported before ADR-031 lacks the client; apply
it with `./scripts/reset-keycloak-realm.ps1` (or `-Target Kubernetes`).
`py -I scripts/check-keycloak-realm.py` checks the client's privileges.

### Self-registration and provisioning

Keycloak's "Register" page creates `PATIENT` accounts only: the realm's default
role contains `PATIENT` and nothing else. `PSYCHOLOGIST` and `ADMIN` are granted
by an administrator. The Admin API's `POST /users` ignores the `id` you send and
adds the default role, which would give a psychologist two ClinicFlow roles.
Provision through `partialImport` with explicit roles instead. It keeps the `id`
and adds no default role:

```powershell
$admin = Get-Content secrets/keycloak/admin.env | ConvertFrom-StringData
$login = @{ grant_type = 'password'; client_id = 'admin-cli'; username = $admin.KC_BOOTSTRAP_ADMIN_USERNAME }
$login['password'] = $admin.KC_BOOTSTRAP_ADMIN_PASSWORD
$token = (Invoke-RestMethod -Method Post -Body $login `
  http://localhost:8180/realms/master/protocol/openid-connect/token).access_token
$user = @{
  id = [guid]::NewGuid().ToString()   # or an id the services already store, to keep ownership
  username = 'new.psychologist@example.test'; email = 'new.psychologist@example.test'
  firstName = 'New'; lastName = 'Psychologist'; enabled = $true; emailVerified = $true
  realmRoles = @('PSYCHOLOGIST'); requiredActions = @('UPDATE_PASSWORD')
  credentials = @(@{ type = 'password'; value = (Read-Host 'Temporary password'); temporary = $true })
}
Invoke-RestMethod -Method Post -Headers @{ Authorization = "Bearer $token" } `
  -ContentType 'application/json' -Body (@{ ifResourceExists = 'FAIL'; users = @($user) } | ConvertTo-Json -Depth 5) `
  http://localhost:8180/admin/realms/clinicflow/partialImport
```

The user sets their own password at the first sign-in. To promote an existing
account in the admin console instead, open the user's **Role mapping**, assign
`PSYCHOLOGIST` (or `ADMIN`), and unassign `default-roles-clinicflow`.

Registration email enumeration (#79) is not solved by the move. Keycloak's
registration form answers an existing email with "This email is already
associated with an existing account.", next to the field, while a new email
creates the account. The login form stays generic and password reset is
disabled, so registration is the only enumeration path. The realm has no
built-in option to hide it. Hiding it would need a custom registration flow, so
the practical control is the gateway's login limit (10 per minute, burst 5) on
Keycloak's `login-actions` paths, where the registration form posts. It applies
on Kubernetes; Compose publishes Keycloak without the gateway. #79 stays open.

## Frontend Development

The frontend lives in `frontend/` and uses Next.js App Router as a lightweight
BFF. Browser code calls only same-origin `/api/*` routes. Those route handlers
call the private gateway with server-only `GATEWAY_URL` in Compose/Kubernetes.
The following direct-service variables are retained only for host-run non-production development:

- `AUTH_SERVICE_URL`
- `PATIENT_SERVICE_URL`
- `APPOINTMENT_SERVICE_URL`
- `CLINICAL_SERVICE_URL`
- `NOTIFICATION_SERVICE_URL`
- `CLINICFLOW_OIDC_ISSUER`, `CLINICFLOW_PUBLIC_URL` and `CLINICFLOW_BFF_CLIENT_SECRET`
  (see [BFF sign-in](#bff-sign-in)); a host-run BFF on `http://localhost:3000`
  uses the Compose Keycloak at `http://localhost:8180`, and that callback is
  registered on the client

For local development without Docker:

```powershell
cd frontend
npm install
npm run dev
```

The default values point to services running on `localhost:8082` through
`localhost:8085`. Copy `frontend/.env.example` and fill in the Keycloak client
secrets from `secrets/keycloak/` when you sign in against the Compose Keycloak.

Frontend validation commands:

```powershell
cd frontend
npm run lint
npm run typecheck
npm run build
```

Playwright smoke test:

```powershell
cd frontend
npx playwright install chromium
npm run test:e2e
```

When the Docker Compose frontend is already running, Playwright reuses
`http://localhost:3000`. Otherwise, it starts `npm run dev` and uses the default
localhost backend service URLs.

The access token is stored only in the `clinicflow_access_token` HttpOnly cookie.
The BFF also sets `clinicflow_csrf` and requires `X-CSRF-Token` on mutating
same-origin API requests. Role-protected BFF routes and dashboard rendering
verify the access token against the realm JWKS and read the profile from its
claims.

## Minimal Smoke Flow

1. Open `https://clinicflow.localhost/login` (or `http://localhost:3000/login` for host-run development).
2. Sign in as the demo psychologist `sofia.almeida@demo.clinicflow.local`.
3. Create a patient from the Patients page.
4. Schedule an appointment from the Appointments page.
5. Create a clinical case from the Clinical page.
6. Add a care plan and a session record.
7. Open the patient's clinical history.
8. Confirm notification history updates from the Notifications page.
9. Sign in as `admin@demo.clinicflow.local`, check backend health on the System
   page and look up `00000000-0000-4000-8000-000000000011`.

## Run Locally with Docker Desktop Kubernetes

Docker Compose remains the fastest development loop. Use Kubernetes when working
on orchestration, probes, network policies, metrics, Prometheus, or Grafana.
The supported CLI line is Helm 4.x. Jenkins pins the validated patch release,
while the deployment script accepts Helm 4 patch updates and checks the major
version before building images. During the Helm 4 migration, installs and
upgrades explicitly retain client-side apply semantics.
The observability release uses `--wait=legacy` for compatibility with the
bundled kube-prometheus-stack admission hooks; the other releases use Helm 4's
watcher strategy.

Run the complete local deployment from the repository root:

```powershell
.\scripts\deploy-local-kubernetes.ps1
```

The script derives a commit/timestamp image tag, builds the local images,
builds ClinicFlow's PostgreSQL image (the official image without `gosu`,
ADR-028) and pins it by an `id-<image id>` tag, so a rebuild that changes
nothing does not restart the database. It generates missing development
credentials and creates or updates `clinicflow-postgres-admin`,
`clinicflow-rabbitmq-admin` and one `<service>-credentials` Secret per service,
and Keycloak's `clinicflow-keycloak-database`, `clinicflow-keycloak-admin`,
`clinicflow-bff-oidc` and `clinicflow-user-lookup-oidc` Secrets. It then
installs observability, the shared platform and Keycloak, upgrades five
independent application releases (four services and the frontend) and the
gateway, and waits for the workloads to become ready. Keycloak's image is pinned
by an `id-<image id>` tag like PostgreSQL's, so it restarts only when the image,
a Keycloak Secret or the realm file changes. `-Component Keycloak` deploys only
Keycloak.
After every platform install or upgrade, the platform release runs the
`clinicflow-platform-bootstrap` hook Job, which creates or rotates the per-service
database roles and RabbitMQ users. Each credential file's checksum is set on its
pods, so a rotated password rolls only the affected workloads.

Upgrade only one application when working on it:

```powershell
.\scripts\deploy-local-kubernetes.ps1 -Component Patient
```

When reusing an existing local image, pass its immutable tag explicitly:

```powershell
.\scripts\deploy-local-kubernetes.ps1 -Component Patient `
  -SkipBuild -ImageTag git-<commit>-<timestamp>
```

The equivalent manual commands are documented below for troubleshooting.

Build the local images with one immutable tag:

```powershell
$imageTag = "git-<commit>-<timestamp>"
docker build -t "clinicflow/patient-service:$imageTag" -f patient-service/Dockerfile .
docker build -t "clinicflow/appointment-service:$imageTag" -f appointment-service/Dockerfile .
docker build -t "clinicflow/clinical-service:$imageTag" -f clinical-service/Dockerfile .
docker build -t "clinicflow/notification-service:$imageTag" -f notification-service/Dockerfile .
docker build -t "clinicflow/frontend:$imageTag" -f frontend/Dockerfile frontend
docker build --target server -t "clinicflow/postgres:$imageTag" docker/postgres
```

Create the platform administrator, per-service and Keycloak client Secrets
from the generated files. The services and the BFF trust the Keycloak realm
(`oidc.*` in the service and frontend charts):

```powershell
kubectl create namespace clinicflow
.\scripts\generate-dev-credentials.ps1
kubectl create secret generic clinicflow-postgres-admin `
  --from-env-file=secrets/platform/postgres.env -n clinicflow
kubectl create secret generic clinicflow-rabbitmq-admin `
  --from-env-file=secrets/platform/rabbitmq.env -n clinicflow
Get-ChildItem secrets/services -Filter *.env | ForEach-Object {
  kubectl create secret generic "$($_.BaseName)-credentials" `
    "--from-env-file=$($_.FullName)" -n clinicflow
}
kubectl create secret generic clinicflow-keycloak-database `
  --from-env-file=secrets/keycloak/database.env -n clinicflow
kubectl create secret generic clinicflow-keycloak-admin `
  --from-env-file=secrets/keycloak/admin.env -n clinicflow
kubectl create secret generic clinicflow-bff-oidc `
  --from-env-file=secrets/keycloak/clinicflow-bff.env -n clinicflow
kubectl create secret generic clinicflow-user-lookup-oidc `
  --from-env-file=secrets/keycloak/clinicflow-user-lookup.env -n clinicflow
```

The platform bootstrap creates Keycloak's database and login from
`clinicflow-keycloak-database`, so install the platform before Keycloak.

Install observability, the platform, and the independent application releases:

```powershell
helm dependency update deploy/helm/observability
helm upgrade --install clinicflow-observability deploy/helm/observability `
  -n monitoring --create-namespace `
  -f deploy/helm/observability/values-docker-desktop.yaml `
  --rollback-on-failure --wait=legacy --server-side=false --timeout 5m

helm upgrade --install clinicflow-platform deploy/helm/platform `
  -n clinicflow -f deploy/helm/platform/values-docker-desktop.yaml `
  --set-string "postgres.image=clinicflow/postgres:$imageTag" `
  --rollback-on-failure --wait=watcher --server-side=false --timeout 5m

docker build --provenance=false -t "clinicflow/keycloak:$imageTag" docker/keycloak
# The realm's demo users get the synthetic password from Demo accounts unless you set your own.
$env:CLINICFLOW_DEMO_PASSWORD ??= Read-Host "Demo password"
helm upgrade --install clinicflow-keycloak deploy/helm/keycloak `
  -n clinicflow --set-string "keycloakx.image.tag=$imageTag" `
  --set-string "global.clinicflow.demoPassword=$env:CLINICFLOW_DEMO_PASSWORD" `
  --rollback-on-failure --wait=watcher --server-side=false --timeout 5m

foreach ($service in "patient", "appointment", "clinical", "notification") {
  helm upgrade --install "clinicflow-$service" deploy/helm/service `
    -n clinicflow -f "deploy/helm/services/$service.yaml" `
    --set-string "image.tag=$imageTag" `
    --rollback-on-failure --wait=watcher --server-side=false --timeout 5m
}

helm upgrade --install clinicflow-frontend deploy/helm/frontend `
  -n clinicflow --set-string "image.tag=$imageTag" `
  --rollback-on-failure --wait=watcher --server-side=false --timeout 5m
```

Inspect or roll back one release without changing the others:

```powershell
helm status clinicflow-patient -n clinicflow
helm history clinicflow-patient -n clinicflow
helm rollback clinicflow-patient <revision> -n clinicflow `
  --wait=watcher --server-side=false --timeout 5m
```

Validate rollouts:

```powershell
kubectl rollout status statefulset/postgres -n clinicflow
kubectl rollout status statefulset/rabbitmq -n clinicflow
kubectl rollout status statefulset/keycloak -n clinicflow
kubectl rollout status deployment/patient-service -n clinicflow
kubectl rollout status deployment/appointment-service -n clinicflow
kubectl rollout status deployment/clinical-service -n clinicflow
kubectl rollout status deployment/notification-service -n clinicflow
kubectl rollout status deployment/frontend -n clinicflow
```

Open the local UI and Grafana:

```powershell
kubectl port-forward svc/clinicflow-observability-grafana 3001:80 -n monitoring
```

Frontend: `https://clinicflow.localhost`. Grafana: `http://localhost:3001` with
`admin` / `clinicflow`.

## Retiring auth-service

`auth-service` was removed in ADR-031. A clean Compose start or a new cluster
has none of its artefacts. An environment created before that keeps them until
you remove them; nothing else uses them.

Compose:

```powershell
docker compose up -d --remove-orphans
docker compose exec -T postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE IF EXISTS clinicflow_auth WITH (FORCE)" -c "DROP ROLE IF EXISTS clinicflow_auth_app" -c "DROP ROLE IF EXISTS clinicflow_auth_owner"'
docker image rm clinicflow-auth-service
Remove-Item secrets/jwt-private.pem, secrets/jwt-public.pem, secrets/services/auth-service.env -ErrorAction SilentlyContinue
./scripts/reset-keycloak-realm.ps1
```

The realm reset adds the `clinicflow-user-lookup` client to a realm imported
before ADR-031; it removes self-registered users. The image name follows your
Compose project; `docker image ls` lists it.

Docker Desktop Kubernetes:

```powershell
helm uninstall clinicflow-auth -n clinicflow --wait
kubectl delete secret clinicflow-jwt auth-service-credentials -n clinicflow --ignore-not-found
kubectl exec postgres-0 -n clinicflow -- sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE IF EXISTS clinicflow_auth WITH (FORCE)" -c "DROP ROLE IF EXISTS clinicflow_auth_app" -c "DROP ROLE IF EXISTS clinicflow_auth_owner"'
./scripts/deploy-local-kubernetes.ps1
./scripts/reset-keycloak-realm.ps1 -Target Kubernetes
```

The deploy script warns while the `clinicflow-auth` release is still installed.
