# Issue #13 implementation response draft

Implemented Traefik as ClinicFlow's shared edge gateway, with Gateway API routing in
Kubernetes and file-provider routing in Compose. Public UI and bearer API hostnames are
separate, and the Next.js BFF uses a private gateway listener. Domain logic, JWT validation,
roles, and ownership remain in their existing services; the BFF retains cookies, CSRF,
role checks, and response projections.

Two independent Helm releases own the controller and application gateway configuration.
The controller chart/proxy/Gateway API baseline is pinned to 41.6.1 / 3.7.13 / 1.6.2;
OCI/image/artifact digests, dependency lock/archive, and mandatory CRD schemas are recorded.
CRD bootstrap/upgrades are explicit platform operations, outside application rollback.
A patient-only deployment cannot upgrade the gateway through the deployment selector.

Compose now publishes the gateway instead of backend/frontend application ports. Both
environments use the same route matrix, including clinical history's clinical-service
ownership. Development HTTPS, response headers, per-router local quotas, bounded timeouts,
internal metrics/dashboard, and access-log redaction are configured. Kubernetes policies
separate business ports from health/metrics, admit only the intended peers, and restrict
controller API/DNS/application egress. Positive and negative live probes now prove these
boundaries in the tested kindnet environment, including namespace spoofing and independent
controller egress checks against a temporary ingress-open target.

The implementation also corrects REST advice so unknown backend subpaths return 404 rather
than a generic 500. Browser routes and valid backend endpoint/payload contracts are retained.

## Acceptance evidence, 2026-10-02

| Gate | Evidence | Result |
| --- | --- | --- |
| Backend unit/MVC tests | Pinned Maven/Java container; 191 tests including five unmatched-path regressions | Passed |
| Frontend quality | ESLint, TypeScript, 39 Vitest tests, production Docker image build | Passed |
| Compose and Helm static validation | Both Compose profiles, all chart lint/render checks; 116 built-in resources valid | Passed |
| Required custom schemas | 33 GatewayClass/Gateway/HTTPRoute/Middleware resources against checksum-verified pinned CRDs; generated-route drift checks | Passed; no gateway custom schemas skipped |
| Static boundaries | Required GATEWAY_URL, no deployed direct business URLs, config checksum, actual pod/policy selectors, private/public Service ports, frontend/backend peer/port assertions | Passed |
| Real proxy contract | 17 collection/item/encoded-ID cases each over HTTP and HTTPS; methods/bodies/query/status/header preservation | Passed |
| Real proxy failure/policy behavior | 429/refill, forwarded-address spoof resistance, independent private quotas, 404 isolation, 15s timeout, backend outage/unrelated availability, no mutation retries, metrics, log redaction | Passed |
| Docker-hosted CI driver | Same HTTP/HTTPS contract wrapper from the existing Jenkins agent container using host.docker.internal, named certificate volumes, and non-root proxy key permissions | Passed |
| Real application/browser flows | Five Playwright tests over HTTP; five over HTTPS, including registration/login/logout, roles, clinical history, admin health, direct JWT/ownership, CSRF, transport cookies | Passed |
| Local CA/SAN identity | Strict proxy-client CA checks for both fixture hostnames; real stack's local CA gives UI 200 / anonymous API 401 without installing OS trust | Passed |
| Live Helm installation and Gateway conditions | Docker Desktop kind, Kubernetes 1.36.1, Helm 4.2.1; ten releases deployed; Gateway and 26 HTTPRoutes accepted/programmed/resolved at current generations | Passed |
| Live Gateway API contract | Installed route specifications cloned into temporary header-selected routes; 17 shared collection/item/encoded-ID cases over strict CA/SAN-verified HTTPS | Passed |
| Live failure and policy behavior | Default quotas, refill, spoofed forwarded addresses, independent private quota, 15s timeout, mutation count of one, fixture outage isolation, real patient outage/BFF JSON 503/recovery, redacted JSON logs | Passed |
| Live application/browser flows | Five Playwright tests over Kubernetes HTTPS, including the full clinical workflow, bearer roles/ownership, cookies/CSRF and admin health; temporary functional quotas restored afterward | Passed, 21.0s |
| Live NetworkPolicy enforcement | kindnet v20260528-9350166c; authorized and denied application/management/private/metrics traffic, wrong-namespace gateway labels, real controller egress denied against ingress-open positive-control targets | Passed |
| Live observability | Gateway Prometheus target UP; Grafana imported the dashboard/datasource; volume, P95 latency, 429 and 5xx queries returned finite samples | Passed |
| Live independent rollback | Gateway public limit changed and restored; patient image digest/tag changed and restored; unrelated images and gateway/controller revisions preserved | Passed |

`kubeconform` still skips pre-existing observability custom kinds; gateway custom schemas
are validated separately and cannot be skipped. Structural validation cannot replace live
CEL/controller acceptance. Browser-only HTTPS error ignoring is opt-in for the uninstalled
local CA; strict certificate/hostname checks run separately. The observed Docker Desktop
client address was 172.23.0.1 in the Compose fixture topology and 10.244.1.1 through the
Kubernetes LoadBalancer, so local NAT can group host clients.

Live acceptance ran on 2026-10-02 using context `docker-desktop`, with two nodes. The
public Service received 172.19.0.6 and was reachable on Windows loopback ports 80/443;
private API 8080 and metrics 9100 remained ClusterIP-only. Application images used the
unique local tag `acceptance-20261002-24c3c099`. The rollback candidate changed image
metadata/digest while keeping patient application binaries identical; this proves the
deployment/release isolation mechanism, not a database migration rollback.

Live execution exposed and corrected native CLI flag forwarding in the PowerShell
deployment helper and a provider-publication race after Gateway conditions became true.
Traffic checks now wait for bounded convergence. Two browser filter interactions now
wait for popup/highlight state instead of assuming keyboard timing; product UI code was
unchanged. Fixture routes/probes were cleaned, original quotas and patient replicas/images
were restored, and CRDs were retained.

Reproducible commands and limitations are in [the gateway runbook](gateway.md).
Implementation decisions and responsibility boundaries are in
[ADR-018](../architecture/decisions/ADR-018-traefik-gateway.md). Detailed local logs are
under ignored reports/gateway-upstream, reports/gateway and reports/gateway-live-20261002;
Jenkins archives its gateway,
platform, and browser test artifacts. No private keys enter Git or those report archives.

Both Compose and live Helm acceptance gates have passed; the evidence is ready for review
and issue #13 closure. This draft has not been posted to GitHub. This remains a local portfolio
prototype with synthetic data. Public ACME/DNS, shared Redis quotas, multiple replicas,
mesh/mTLS, gateway JWT offloading, and distributed tracing are deferred follow-ups.
