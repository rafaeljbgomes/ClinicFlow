# Gateway deployment and acceptance

ClinicFlow uses synthetic demonstration data. Compose and live Kubernetes acceptance
passed on 2026-10-02 for the tested local prototype. See the
[issue evidence](issue-13-response.md) for the exact cluster and validation boundaries.

## Addresses and route contract

- UI/BFF: `https://clinicflow.localhost`; existing browser `/api/**` routes stay unchanged.
- Bearer API: `https://api.clinicflow.localhost/api/v1/**`.
- Keycloak (Kubernetes only): `https://auth.clinicflow.localhost`, the issuer's origin (ADR-027).
- Kubernetes BFF URL: `http://clinicflow-gateway-api.ingress-system.svc.cluster.local:8080`.
- Compose BFF URL: `http://gateway:8080`, unpublished.

| Facade prefix/path | Service | Rewrite |
| --- | --- | --- |
| `/api/v1/patients/**` | patient:8082 | `/patients/**` |
| `/api/v1/appointments/**` | appointment:8083 | `/appointments/**` |
| `/api/v1/clinical/clinical-cases/**` | clinical:8085 | `/clinical-cases/**` |
| `/api/v1/clinical/session-records/**` | clinical:8085 | `/session-records/**` |
| `/api/v1/clinical/practice-profile` | clinical:8085 | `/practice-profile` |
| `/api/v1/clinical/patients/{id}/clinical-history` | clinical:8085 | `/patients/{id}/clinical-history` |
| `/api/v1/notifications/**` | notification:8084 | `/notifications/**` |

`deploy/gateway/routes.json` drives both provider configurations. After a reviewed route
change run `python scripts/gateway/render-config.py`; `--check` detects drift. Gateway
facade versions do not rename backend controllers or move business logic.

### Identity routes (Kubernetes)

The auth host routes four path prefixes of the `clinicflow` realm to `keycloak:8080`, all
with the secure headers. Everything else on it, including `/admin`, `/realms/master` and
the account console, returns 404 from the edge.

| Route | Path prefix | Limit |
| --- | --- | --- |
| `identity-oidc` | `/realms/clinicflow/protocol/openid-connect` | public |
| `identity-login` | `/realms/clinicflow/login-actions` | auth (10/min, burst 5) |
| `identity-discovery` | `/realms/clinicflow/.well-known` | public |
| `identity-resources` | `/resources` | public |

The OIDC prefix covers authorization, registration, the token endpoint, JWKS, logout and
the session iframe; the token endpoint requires client authentication and the realm
allows no password grant to the BFF. `login-actions` carries the credential, registration
and required-action posts, so the login limit sits there. The admin console is reached
with `kubectl port-forward svc/keycloak 8180:8080 -n clinicflow`. Keycloak trusts
`X-Forwarded-*` from any peer NetworkPolicy admits (`proxy-trusted-addresses` is unset):
every Traefik entrypoint drops client-sent forwarding headers and writes its own, and only
the gateway, the BFF and the four resource servers can reach Keycloak's HTTP port. A
compromised one of those pods could still put a false client address in Keycloak's
events. A deployment whose proxies have fixed addresses should set the option.

## Compose HTTPS

Prerequisites: Docker/Compose, PowerShell 7, OpenSSL. Port 80/443 must be free; override
`CLINICFLOW_GATEWAY_HTTP_PORT`/`CLINICFLOW_GATEWAY_HTTPS_PORT` together with browser URLs
if another local application uses them. The HTTPS override also controls redirect ports.

```powershell
./scripts/generate-dev-credentials.ps1
./scripts/gateway/New-DevelopmentCertificate.ps1
# Trust secrets/edge/ca.crt manually in the browser's certificate settings if desired.
docker compose up --build --detach
```

The script retains existing valid CA/certificate files. It reissues the leaf from the same
CA when the certificate expires within a day or lacks one of the three hostnames, and
`-Renew` reissues it unconditionally. Browser trust is a manual choice. Never commit/share ca.key or edge.key.
On Unix the CA key is owner-only; the leaf key is owner/group-readable. The script
sets CLINICFLOW_TLS_GROUP to the caller group for the non-root proxy. If running the
script in a separate process, pass that group ID to Compose explicitly. Only the leaf
certificate/key are mounted; the gateway cannot read the CA key. Management 9000, ping 9001, metrics
9100, private 8080, and application 3000/8082–8085 have no host port mappings. PostgreSQL
and RabbitMQ local tools keep their existing published development ports.

For a host-run `npm run dev` with host-run Spring services, existing direct URL variables
remain supported without GATEWAY_URL. To call a Compose gateway from a host-run BFF, use
`GATEWAY_URL=https://api.clinicflow.localhost` and trust the local CA for Node explicitly;
its health reader also needs separately reachable management endpoints. The deployed
frontend already has private DNS/management access. There is no error fallback.

## Isolated HTTP CI and real-proxy tests

The CI overlay disables TLS/redirects/secure cookies, publishes only its allocated gateway
HTTP port, and permits `localhost`, `127.0.0.1`, `host.docker.internal` as UI host aliases.
API routes still require the explicit API Host header. Backend readiness is checked with
wget from the gateway container against management 9000.

```powershell
./scripts/ci/Build-Images.ps1 -RunId issue13
./scripts/ci/Start-IsolatedCompose.ps1 -RunId issue13
./scripts/ci/Invoke-SystemPlaywright.ps1 -RunId issue13
./scripts/ci/Stop-IsolatedCompose.ps1 -RunId issue13
```

`node scripts/gateway/test-contract.cjs` starts and removes its own isolated echo-fixture
project with an ephemeral named certificate volume; it works with a remote Docker daemon
without host bind mounts. The CI wrapper passes host.docker.internal as its runtime host.
The ephemeral CA key stays only in the bootstrap container, and only its public certificate
and leaf material reach the gateway. It checks the common matrix with collection/item/encoded IDs, methods, bodies,
queries, explicit host routing, private routing, security headers, quotas and refill,
spoofed forwarding, timeouts, outage isolation, mutation counts, metrics, and redacted
JSON access logs. Repeat with `$env:GATEWAY_TEST_TLS='true'` to verify CA/SAN identity,
HTTPS/HSTS, and POST 308 redirects; remove that variable after the run. Fixtures prove
proxy behavior; the real backend/browser tests prove authorization and product flows.

For full HTTPS browser acceptance add `deploy/gateway/compose.https-acceptance.yaml` after
the main and CI files. Allocate free HTTP/HTTPS ports in those environment variables,
then run Playwright with `PLAYWRIGHT_BASE_URL=https://clinicflow.localhost:<https-port>`,
`PLAYWRIGHT_SKIP_WEBSERVER=true`, `PLAYWRIGHT_REQUEST_BASE_URL=https://127.0.0.1:<https-port>`
for Node request-context DNS, and `PLAYWRIGHT_IGNORE_HTTPS_ERRORS=true`. This last flag
is only for the isolated test browser; it does not install a CA or bypass the separate
Node contract's certificate/SAN checks. The overlay enables actual Secure cookies.

## Helm preflight and installation

Check `kubectl config current-context`, `kubectl version -o json`, CNI DaemonSets/plugin
configuration, LoadBalancer support, available ports, Helm 4, and certificate validity.
The pinned Gateway API bundle also installs safe-upgrade admission policies; the cluster
must support those admission APIs. A deployment without an enforcing CNI is not isolation
acceptance. The installer prints versions/DaemonSets, derives API Service/endpoint CIDRs,
and waits for Gateway/HTTPRoute conditions at the current generation.

```powershell
./scripts/deploy-local-kubernetes.ps1
# Independent infrastructure updates:
./scripts/deploy-local-kubernetes.ps1 -Component GatewayController -SkipBuild
./scripts/deploy-local-kubernetes.ps1 -Component Gateway -SkipBuild
# Patient delivery never upgrades the gateway:
./scripts/deploy-local-kubernetes.ps1 -Component Patient -SkipBuild -ImageTag git-<commit>-<timestamp>
```

Order: observability, platform, Keycloak, explicit CRD bootstrap, controller, applications,
gateway routes, conditions, real HTTPS requests. The gateway step stops early when the
`keycloak` Service is missing, because the identity routes could never resolve. The
`clinicflow-edge-tls` Secret lives outside the Helm release; the installer creates it, and
re-applies it whenever `secrets/edge/edge.crt` changes, such as after a reissued leaf.
Provider watches and policy namespaces are configured together for custom namespaces.

CRD upgrades require `Initialize-GatewayCrds.ps1 -Upgrade` (or installer
`-UpgradeGatewayCrds`) after reviewing pinned artifacts, cluster compatibility, stored
versions, conversion, and admission safeguards. Automatic application uninstall/rollback
must not delete or downgrade CRDs. Controller installation uses `--skip-crds` and the
committed lock/archive; application operations use `--server-side=false`.

## Static and live gates

```powershell
# Needs Python 3, Node (frontend npm ci), Docker CLI, Helm 4, and kubeconform.
./scripts/ci/Test-Platform.ps1
./scripts/gateway/Wait-GatewayRoutes.ps1
./scripts/gateway/Test-GatewayTraffic.ps1
node scripts/gateway/test-kubernetes-contract.cjs
./scripts/gateway/Test-GatewayIsolation.ps1
./scripts/gateway/Test-KeycloakTokens.ps1
./scripts/gateway/Test-KeycloakEdge.ps1     # last before the browser run: it spends the login burst
./scripts/gateway/Test-GatewayBrowser.ps1
./scripts/gateway/Test-GatewayObservability.ps1
./scripts/gateway/Test-IndependentRollback.ps1 -PatientImageTag <available-immutable-tag>
```

Pinned custom schemas are mandatory: the validator checks every GatewayClass/Gateway/
HTTPRoute/Middleware and fails missing schemas. kubeconform validates built-in resources;
the legacy observability check still skips external custom schemas. CEL and controller
semantics require a live API server and current-generation Accepted/Programmed/ResolvedRefs.
On Windows, the local-CA curl check uses best-effort revocation because the development
CA has no online revocation service; CA and hostname verification remain enabled.
The traffic check verifies CA trust, UI routing, unauthenticated API rejection, and closed
management paths. Current-generation conditions can precede middleware/configuration
publication; real traffic must also converge within a bounded deadline.

The live contract test clones the installed business HTTPRoute specifications into
temporary routes selected by a unique request header, with isolated echo backends. It
checks the same matrix as Compose through the actual Gateway API controller. Normal
application routing remains available. It requires the auth quota on exactly the BFF
`/api/auth/login` and `/api/auth/register` routes and Keycloak's `login-actions`, and
clones the two BFF routes onto the fixture to check their quotas, refill and forged
`X-Forwarded-For` handling, and that the private listener does not serve them. It then
signs in through the BFF and Keycloak (one `login-actions` token) and briefly scales the
real patient deployment to zero to verify BFF JSON 503 errors, unrelated service
availability and recovery, restoring its original replica count in finally. Run it in the
local acceptance environment.
Override GATEWAY_TEST_RUNTIME_HOST if loopback does not reach the LoadBalancer.

Isolation probes grant egress only to their temporary probe identities
so a negative ingress test cannot pass merely because probe egress was blocked. They clean
only resources bearing their unique run label. A gateway label in the application namespace
must still be denied. An ingress-open temporary target with frontend positive controls
separately proves controller egress denial on database/broker ports; actual DB/broker
destinations are checked too. Preserve CNI/version evidence and positive-control outputs.

The browser wrapper temporarily relaxes public/auth quotas for functional workflows and
restores the previous Helm revision and process environment in finally. It permits the
test browser to ignore the uninstalled development CA; strict CA/SAN checks remain separate
required gates. The observability script requires a real target UP, the imported Grafana
dashboard/datasource and finite samples for every panel; generate 429/5xx traffic with
the live contract test first.

The rollback script changes one harmless gateway rate value and restores the previous
revision, then upgrades/rolls back only patient-service and compares unrelated images and
gateway/controller revisions. These are deliberate live operations for the local test
environment; supply an existing immutable patient image. CRDs are not part of rollback.

On the tested Docker Desktop kind cluster, local images were available to Kubernetes
without a separate registry upload. Record that check for each environment. Initial
observability image pulls were populated through Docker Desktop's local cache. Acceptance
logs for 2026-10-02 are under ignored reports/gateway-live-20261002; these supplement the
earlier Compose/schema evidence rather than replacing it.

## Observability and operational limits

Prometheus scrapes the private metrics Service with a ServiceMonitor; Grafana discovers
`clinicflow-gateway-dashboard` through its dashboard label. Verify target UP, request
volume, P95 latency, 429, and 5xx panels with real requests. Neither API host exposes metrics,
ping, Actuator, dashboard, or administration. Access logs allow timestamp/method/router/
service/status/duration and omit raw paths, queries, bodies, and all request headers.

Quotas are per router and per instance. Recreate upgrades briefly interrupt the edge and
reset buckets. One replica and no HPA are intentional. Docker Desktop may present a shared
NAT address for multiple host clients; record the effective address, and do not treat local
limits as distributed/account quotas. Public proxy deployments need explicit trusted-IP
configuration. CORS is disabled; future origins require an allowlist. Redis, public ACME,
multiple replicas, mesh/mTLS, JWT offload, and distributed tracing remain follow-ups.
