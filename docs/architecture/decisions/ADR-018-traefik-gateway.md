# ADR-018: Traefik gateway and Kubernetes edge

Status: implemented; live Kubernetes acceptance pending. Date: 2026-10-02.

## Decision

Use Traefik 3.7.13 as one shared edge proxy: file-provider routing in Compose and
Gateway API 1.6.2 standard-channel routing in Kubernetes. Wrap the pinned upstream
Helm chart 41.6.1. Exact OCI/artifact/image digests are recorded in
`deploy/gateway/provenance.json`; the dependency lock and chart archive are committed.

The public UI host is `clinicflow.localhost`; the public bearer-token API host is
`api.clinicflow.localhost`. On Kubernetes a third host, `auth.clinicflow.localhost`,
serves Keycloak's `clinicflow` realm through an allowlist of path prefixes (ADR-027,
#89); Keycloak's admin console and `master` realm have no route. The BFF calls a separate unpublished HTTP listener on
port 8080. It never falls back to a backend when gateway requests fail. Application
Services remain ClusterIP and Compose publishes no backend or frontend application
ports. Containerized Actuator traffic uses separate management port 9000.

## Responsibilities and dependencies

The gateway owns TLS termination, host/path routing, the `/api/v1` facade, connection
limits, local quotas, security response headers, and edge telemetry. It has no
business use cases, database/broker access, or domain dependencies. Controllers keep
their original paths. Services still validate JWT signatures, roles, and ownership;
Next.js still owns HttpOnly cookies, CSRF, role checks, and response projections.
Health reading is an explicit BFF-to-management exception. Direct service URLs remain
available only to the non-production host-run development workflow.

`deploy/gateway/routes.json` is the reviewed contract. The renderer produces both
provider artifacts and CI rejects drift. Clinical history belongs to clinical-service:
`/api/v1/clinical/patients/{id}/clinical-history`. Unknown route families and hosts
return 404 without UI fallback; REST advice maps unmapped backend subpaths to 404.
Neither Actuator nor gateway administration is a business route.

## Release and CRD ownership

`clinicflow-gateway-controller` in `ingress-system` owns Traefik, its ServiceAccount,
RBAC, GatewayClass, LoadBalancer/private/metrics Services, and controller policy.
`clinicflow-gateway` in `clinicflow` owns Gateway, HTTPRoutes, Middleware policies,
and the dashboard. Backends, routes, and the existing TLS Secret share the application
namespace; allowedRoutes is Same. Providers watch only these two namespaces; legacy
Ingress, default IngressClass/default Gateway, dashboard, insecure API, and automatic
version/usage checks are disabled.

Bootstrap versioned Gateway API and Traefik CRDs before Helm with the explicit platform
script. Its server-side apply handles large CRDs; application Helm operations retain
Helm 4 client-side apply, watcher waits, and rollback-on-failure. CRDs and Gateway API
safe-upgrade admission resources live outside application releases and survive their
uninstall/rollback. Existing unmanaged/different CRDs require the explicit -Upgrade
operation after review. Do not force conflicts or automatically downgrade CRDs.

A patient-only deployment does not upgrade either gateway release. Static proxy
arguments change the pod template; frontend runtime configuration has a checksum.
Gateway/Middleware changes are watched dynamically and do not require a proxy restart.

## TLS, resource budget, and limits

Development TLS uses a local CA and a certificate with both DNS names. Keys live under
ignored `secrets/edge`; only leaf material is mounted read-only, with a Unix reader group
for the non-root proxy; no OS/browser trust store is changed automatically. Kubernetes
listeners use actual pod ports 8000/8443 behind Service ports 80/443. Redirects preserve
mutation methods: Gateway API uses 308; Compose's supported permanent RedirectScheme
uses 301 for GET and 308 for other methods. CI explicitly uses HTTP, disables redirects
and secure cookies, and allows UI aliases for its runtime host only in that profile.

Use one replica, Recreate upgrades (brief edge downtime), no autoscaling, requests
100m/128Mi and limits 500m/256Mi. Public API and BFF API defaults are 20/s burst 40;
login/registration 10/minute burst 5; private API 100/s burst 200. These limits are
instance/router-local, reset on restart, and are separate for routes and hosts. They
are not aggregate tenant/account quotas. Functional CI relaxes public limits and a
separate real-proxy test verifies throttling/refill. Redis is intentionally deferred.

Client identity comes from the observed socket address. No forwarded-address source
criterion or insecure forwarded-header trust is enabled. Docker Desktop NAT may group
host-originating clients behind one address; fixture evidence records the actual address.
Deploying behind another proxy requires an explicit reviewed trusted-IP list.

Send nosniff, DENY framing, no-referrer, and HTTPS-only HSTS. CORS is disabled by default.
Dial timeout is 2s, response-header timeout 15s, listener read/write/idle bounds are
15s/30s/60s. BFF retains its 10s deadline. No retry middleware is configured. Plain edge
errors become the existing BFF JSON shape; 429 and Retry-After survive when provided.
Authorization and correlation headers survive forwarding.

## Telemetry and isolation

Internal Prometheus metrics and a gateway dashboard cover volume, P95 latency, 429, and
5xx. JSON access logging keeps only timestamp/method/router/service/status/duration;
request headers, paths, query values, bodies, tokens, and cookies are omitted. General
INFO logs are operational logs, separate from the access-log schema.

Policies admit gateway pods to application ports, frontend/monitoring to management
9000, frontend to private API 8080, and monitoring to metrics 9100. Namespace and pod
selectors occur in the same allowed peer. Controller egress permits application ports,
DNS, and narrowly discovered API Service/endpoint CIDRs; no databases or broker.
Declarations alone prove no isolation. Test authorized AND unauthorized traffic in an
enforcing CNI and preserve results before closing issue #13.

## Acceptance and deferred work

See [gateway deployment guide](../../infrastructure/gateway.md) and
[issue evidence draft](../../infrastructure/issue-13-response.md). Compose HTTP/HTTPS,
static schemas, browser/API security, failures, and live Helm/CNI/rollback checks are
separate gates. A missing cluster context cannot satisfy the live gates.

All these gates passed on 2026-10-02 in Docker Desktop kind with Kubernetes 1.36.1,
Helm 4.2.1 and kindnet v20260528-9350166c. Traffic probes established actual policy
enforcement, including namespace/pod selector conjunction and isolated controller egress.
Real requests also established that Gateway conditions can precede middleware publication;
bounded traffic convergence remains part of deployment acceptance. The issue evidence
records the tested limits, observability, outage/recovery and independent rollback results.

The target remains a local portfolio prototype with synthetic data. Public DNS/ACME,
shared Redis quotas, multiple gateway replicas, service mesh/mTLS, gateway JWT offload,
and distributed tracing require separate decisions.

## Primary references

- [Traefik Gateway API provider](https://doc.traefik.io/traefik/reference/routing-configuration/kubernetes/gateway-api/)
- [Gateway API rewrites/redirects](https://gateway-api.sigs.k8s.io/guides/user-guides/http-redirect-rewrite/)
- [Helm CRD lifecycle](https://helm.sh/docs/chart_best_practices/custom_resource_definitions/)
- [Traefik local rate limiting](https://doc.traefik.io/traefik/reference/routing-configuration/http/middlewares/ratelimit/)
- [Kubernetes NetworkPolicy](https://kubernetes.io/docs/concepts/services-networking/network-policies/)

Current primary documentation and the exact pinned chart/proxy govern configuration;
Clean Architecture keeps framework changes in REST adapters and deployment infrastructure.
