# ADR-028: ClinicFlow PostgreSQL image without gosu

Status: implemented. Date: 2026-10-22. Issue: #35.

## Context

The platform ran the official `postgres:16.15-alpine` image for PostgreSQL and
its bootstrap. That image ships `/usr/local/bin/gosu` 1.19, built with Go
1.24.6, so Trivy reports High Go standard-library CVEs for the `postgres` and
`postgres-bootstrap` targets of the system scan. They were accepted in
`.ci/security-baseline.json` until 2026-11-19.

Neither upstream has a fix. `gosu` 1.19 is still the latest release, and the
official image republished on 2026-10-22 (`sha256:f4610795…`) still carries the
same binary. The entrypoint only calls `gosu` to drop from root to the
`postgres` user (UID 70) when the container starts as root. Kubernetes already
starts the pods as UID 70, so the binary was never executed there. Compose,
however, started PostgreSQL as root and depended on it.

## Decision

ClinicFlow builds its own PostgreSQL image from `docker/postgres/Dockerfile`:

- The `server` target is the official image, pinned by digest, with
  `/usr/local/bin/gosu` removed and `USER 70:70`. Started as UID 70, the
  official entrypoint runs PostgreSQL directly and never looks for `gosu`.
- The `bootstrap` target builds on `server` and adds the bootstrap SQL. It
  replaces the separate `docker/postgres/bootstrap/Dockerfile`, so one digest
  pin covers both images.

Compose builds `clinicflow/postgres:local` and
`clinicflow/postgres-bootstrap:local`, and CI tags them
`ci-<run>`. The system scan therefore inventories the images that actually run.
The platform chart's default image is `clinicflow/postgres:local`.
`scripts/deploy-local-kubernetes.ps1` builds the image for `All` and `Platform`,
then deploys it under an `id-<12 hex of the image ID>` tag. A rebuild that
changes nothing keeps that tag and does not restart the StatefulSet. The Helm
security contexts are unchanged.

Dependabot watches `docker/postgres`, so new official digests still arrive as
weekly PRs.

## Consequences

- The `gosu` findings disappear from the scan instead of being tracked.
  Removing their baseline entries needs a separate policy-only PR, because the
  security policy rejects mixing exemptions with code.
- ClinicFlow now owns an image. Every official update must go through the
  Dockerfile pin (Dependabot) and be rebuilt locally; a stale local
  `clinicflow/postgres:local` is not refreshed by `docker compose up` without
  `--build`.
- Compose now runs PostgreSQL as UID 70 too. Existing `postgres-data` volumes
  are compatible, because the official image always initialised them as
  `postgres`. A volume created as another user would need its ownership
  changed.
- Compose's `postgres-bootstrap` is the one exception to UID 70. It reads the
  generated credential files, which are owner-only (600 in a 700 directory)
  and owned by the host user, so on Linux UID 70 cannot open them. That
  one-shot `psql` client runs as root with every capability dropped except
  `DAC_READ_SEARCH` and with `no-new-privileges`. It never starts PostgreSQL,
  so it does not need `gosu`. In Kubernetes the Job reads Secrets as UID 70.
- The Testcontainers integration tests still use the official image. They are
  not part of the system scan, and Testcontainers starts it as root.
- If upstream ships `gosu` with a current Go runtime, keeping the removal
  still costs nothing. Reverting to the official image only needs the image
  references restored.

## Alternatives considered

- **Keep the baseline acceptances.** No upstream fix has a date, so this means
  renewing them every 30 days for a binary ClinicFlow does not need.
- **Rebuild `gosu` with a current Go toolchain.** This keeps the root start
  path in Compose, but it means owning a Go build of a security-sensitive
  binary for a step that UID 70 avoids entirely.
- **Debian-based official image.** It also ships `gosu`.
