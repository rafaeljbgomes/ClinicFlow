# Local Development

## Prerequisites

- Java 21
- Maven Wrapper 3.9.16 (the repository wrapper is authoritative)
- Node.js 24.19.0 and npm 11 for host-side frontend work
- Docker Desktop or Docker Engine
- OpenSSL available in the shell path

## Generate Development JWT Keys

The services use asymmetric JWTs. Generate local-only keys before running Docker
Compose:

```powershell
.\scripts\generate-dev-jwt-keys.ps1
```

The generated files are written under `secrets/` and ignored by Git.

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
docker compose up --build
```

Docker Compose includes a one-shot `postgres-bootstrap` service that runs before
the Spring Boot services. It creates any missing service databases in existing
PostgreSQL volumes and clears incompatible legacy `patients.phone` values so the
strict E.164 Flyway migration can be applied locally.

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

Service ports:

- Frontend: `http://localhost:3000`
- Auth Service: `http://localhost:8081`
- Patient Service: `http://localhost:8082`
- Appointment Service: `http://localhost:8083`
- Clinical Service: `http://localhost:8085`
- Notification Service: `http://localhost:8084`
- RabbitMQ Management: `http://localhost:15672`

RabbitMQ credentials for local development are `clinicflow` / `clinicflow`.

## Frontend Development

The frontend lives in `frontend/` and uses Next.js App Router as a lightweight
BFF. Browser code calls only same-origin `/api/*` routes. Those route handlers
call the Spring Boot services using the following environment variables:

- `AUTH_SERVICE_URL`
- `PATIENT_SERVICE_URL`
- `APPOINTMENT_SERVICE_URL`
- `CLINICAL_SERVICE_URL`
- `NOTIFICATION_SERVICE_URL`

For local development without Docker:

```powershell
cd frontend
npm install
npm run dev
```

The default values point to services running on `localhost:8081` through
`localhost:8085`. Copy `frontend/.env.example` if custom service URLs are
needed.

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
same-origin API requests.

## Minimal Smoke Flow

1. Open `http://localhost:3000/register`.
2. Register a psychologist account.
3. Create a patient from the Patients page.
4. Schedule an appointment from the Appointments page.
5. Create a clinical case from the Clinical page.
6. Add a care plan and a session record.
7. Open the patient's clinical history.
8. Confirm notification history updates from the Notifications page.
9. Check backend health on the System page.

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
generates missing development JWT keys, creates or updates the Kubernetes
Secret, installs observability and the shared platform, upgrades six independent
application releases, and waits for the workloads to become ready.

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
docker build -t "clinicflow/auth-service:$imageTag" -f auth-service/Dockerfile .
docker build -t "clinicflow/patient-service:$imageTag" -f patient-service/Dockerfile .
docker build -t "clinicflow/appointment-service:$imageTag" -f appointment-service/Dockerfile .
docker build -t "clinicflow/clinical-service:$imageTag" -f clinical-service/Dockerfile .
docker build -t "clinicflow/notification-service:$imageTag" -f notification-service/Dockerfile .
docker build -t "clinicflow/frontend:$imageTag" -f frontend/Dockerfile frontend
```

Create the JWT Secret from the local development keys:

```powershell
kubectl create namespace clinicflow
kubectl create secret generic clinicflow-jwt `
  --from-file=jwt-private.pem=secrets/jwt-private.pem `
  --from-file=jwt-public.pem=secrets/jwt-public.pem `
  -n clinicflow
```

Install observability, the platform, and the independent application releases:

```powershell
helm dependency update deploy/helm/observability
helm upgrade --install clinicflow-observability deploy/helm/observability `
  -n monitoring --create-namespace `
  -f deploy/helm/observability/values-docker-desktop.yaml `
  --rollback-on-failure --wait=legacy --server-side=false --timeout 5m

helm upgrade --install clinicflow-platform deploy/helm/platform `
  -n clinicflow -f deploy/helm/platform/values-docker-desktop.yaml `
  --rollback-on-failure --wait=watcher --server-side=false --timeout 5m

foreach ($service in "auth", "patient", "appointment", "clinical", "notification") {
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
kubectl rollout status deployment/auth-service -n clinicflow
kubectl rollout status deployment/patient-service -n clinicflow
kubectl rollout status deployment/appointment-service -n clinicflow
kubectl rollout status deployment/clinical-service -n clinicflow
kubectl rollout status deployment/notification-service -n clinicflow
kubectl rollout status deployment/frontend -n clinicflow
```

Open the local UI and Grafana:

```powershell
kubectl port-forward svc/frontend 3000:3000 -n clinicflow
kubectl port-forward svc/clinicflow-observability-grafana 3001:80 -n monitoring
```

Frontend: `http://localhost:3000`. Grafana: `http://localhost:3001` with
`admin` / `clinicflow`.
