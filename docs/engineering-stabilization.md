# Engineering stabilization workflow

ClinicFlow uses Jenkins as its only CI orchestrator for this stabilization phase. The pipeline is source-controlled in the repository, runs on a dedicated local Linux agent, and delegates validation to the PowerShell scripts in `scripts/ci`. No images are published and no environment is deployed.

## Supported toolchain

- Java 21 and Maven Wrapper 3.9.14
- Node.js 24 and npm 11
- Spring Boot 3.5.16 and Next.js 16.2.11
- RabbitMQ 4.3.4
- Docker Desktop using Linux containers
- PowerShell 7

The Maven Wrapper is the authoritative Maven entry point. Use `./mvnw` on Linux/macOS and `.\mvnw.cmd` on Windows. Frontend dependencies must be installed with `npm ci`; do not use an uncommitted lockfile.

## Direct validation

Every Jenkins stage can be run from a PowerShell 7 terminal at the repository root:

```powershell
pwsh -File ./scripts/ci/Test-RepositoryHygiene.ps1
pwsh -File ./scripts/ci/Invoke-BackendFast.ps1
pwsh -File ./scripts/ci/Invoke-Frontend.ps1
pwsh -File ./scripts/ci/Test-Platform.ps1
pwsh -File ./scripts/ci/Invoke-BackendFull.ps1
pwsh -File ./scripts/ci/Build-Images.ps1
pwsh -File ./scripts/ci/Invoke-ComposePlaywright.ps1 -RunId local-full
```

The Compose-backed script derives an isolated project name from `RunId`, uses fresh named volumes, and never targets the developer's default Compose project. If a run is interrupted, clean up only that run:

Because the build agent talks to Docker Desktop through the host socket, paths
inside the agent container are not valid host bind-mount sources. The runner
therefore adds `docker-compose.ci.yml`: PostgreSQL bootstrap SQL is packaged in
an image and disposable development JWT keys are generated in an isolated named
volume. The normal local Compose workflow and host-side key files are unchanged.
The Jenkinsfile also passes `host.docker.internal` explicitly because browser
tests execute inside the agent; direct host execution keeps the script's
`127.0.0.1` default. The script runs `npm ci` itself before Playwright, so it
also works from a clean checkout instead of relying on an earlier pipeline
stage's workspace state. The CI overlay explicitly disables the auth cookie's
`Secure` attribute because this trusted local stack serves plain HTTP;
production mode remains secure by default and normal Compose configuration does
not set the override.

```powershell
pwsh -File ./scripts/ci/Stop-IsolatedCompose.ps1 -RunId local-full
```

Reports are written below module `target` directories, `coverage-report/target`, `frontend/test-results`, `frontend/playwright-report`, and `reports`. These paths are ignored by Git.

## Local Jenkins

The controller and agent are separate from the application stack. The controller has no executors or Docker socket. The single dedicated agent has the `clinicflow-ci` label and holds the build tools, caches, workspace, and Docker Desktop socket access.

Bootstrap secrets once, then build and start the stack:

```powershell
pwsh -File ./deploy/jenkins/Bootstrap-Jenkins.ps1
docker compose --file ./deploy/jenkins/compose.yml build
docker compose --file ./deploy/jenkins/compose.yml up --detach
```

Open `http://127.0.0.1:8080`, sign in as `admin` using the ignored `deploy/jenkins/secrets/admin-password` file, and run `clinicflow-engineering-stabilization` manually. Select `FAST` for the normal quality gates or `FULL` for mutation, full Maven verification, image builds, isolated Compose, and Playwright validation.

Configuration as Code seeds the job and controller configuration. The default job reads this branch from `file:///workspace/clinicflow`. Override the source without editing the `Jenkinsfile` by copying `.env.example` to an ignored `.env` beside the Jenkins Compose file and setting:

```dotenv
CLINICFLOW_JENKINS_SCM_URL=https://github.com/owner/clinicflow.git
CLINICFLOW_JENKINS_BRANCH=*/fix/engineering-stabilization
```

The Docker socket grants root-equivalent authority over the host. This stack is therefore for a trusted, single-user workstation only. Do not expose the controller beyond loopback or reuse this design for untrusted or shared builds; use isolated ephemeral agents in those environments.

To prove controller reproducibility, stop the stack, remove only its named volumes, and start it again after confirming no build history must be retained:

```powershell
docker compose --file ./deploy/jenkins/compose.yml down --volumes
docker compose --file ./deploy/jenkins/compose.yml up --detach
```

## Future remote integration

When a remote Git repository exists, replace the SCM URL as described above and add its credentials through a local Jenkins secret source. A repository webhook can then trigger the seeded job, and a commit-status integration can publish the Jenkins result. Do not recreate the full pipeline in GitHub Actions. If a hosted check becomes necessary, keep it thin and delegate validation to the same `scripts/ci` entry points.

Artifact publication and deployment are intentionally absent. They require decisions about a remote repository, image registry, environment credentials, promotion rules, and deployment targets and belong to a later CD phase.

## RabbitMQ state and upgrades

The local RabbitMQ data is disposable and now lives in a named Compose volume. Recreating that local broker is supported when its version changes.

Do not attach an existing persistent RabbitMQ 3.13 data directory directly to RabbitMQ 4.3. A persistent installation must follow the supported `3.13 -> 4.2 -> 4.3` upgrade sequence, or use a blue-green migration with explicit data transfer and rollback planning.

When dependency metadata changes, regenerate `package-lock.json` with Node 24/npm 11 and prove a clean `npm ci`. The Jenkins agent provides that same major-version environment, avoiding dependence on a developer-specific Node installation.
