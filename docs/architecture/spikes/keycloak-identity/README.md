# Keycloak identity spike (issue #85)

A throwaway spike that backs [ADR-027](../../decisions/ADR-027-keycloak-identity-provider.md).
It is not part of the ClinicFlow stack, and nothing outside this folder uses it.
#86 builds the real Compose service and realm, using the findings in ADR-027.

## What it checks

| Question | How |
|---|---|
| Can a user keep a fixed UUID, so `sub` equals it? | The realm file imports the five demo users with their `DemoDataBootstrap` UUIDs. The `admin` leg also tries the Admin API `POST /users` and `partialImport` with fixed ids. |
| Does the token carry the ADR-027 contract? | The `login` leg runs the authorization-code flow with PKCE as a browser on the host. The `bff` leg exchanges the code from a container on the private network, verifies the RS256 signature against the JWKS, and records the claims. |
| Is there one issuer for the browser and the private network? | The `bff` leg reads discovery, JWKS and tokens only through `http://keycloak:8080`. The browser leg uses only `http://localhost:8180`. |
| Does self-registration yield only `PATIENT`? | The `register` leg fills in Keycloak's registration form, then decodes the resulting token. The `admin` leg lists each user's direct roles. |
| Is the password grant refused? Is a reused refresh token refused? | The `bff` leg tries both. |
| What do memory and startup cost? | `Run-Spike.ps1` times a cold start and a warm restart, and takes `docker stats` snapshots. |

## Run it

Requires Docker Desktop, PowerShell 7 and Python 3 (`py`). Port 8180 must be
free on 127.0.0.1.

```powershell
./Run-Spike.ps1                                   # optimized image, 1 GiB limit
./Run-Spike.ps1 -MemoryLimit 768m
./Run-Spike.ps1 -Mode auto-build                  # upstream image with plain `start`
./Run-Spike.ps1 -Keep                             # leave it up: http://localhost:8180/admin
docker compose down -v                            # clean up after -Keep
```

The first run writes `.env` with random, spike-only values: the database
password, the bootstrap admin password (user `spike-admin`), the BFF client
secret and the demo password. `.env` and the evidence in `out/` are
Git-ignored. The realm file contains only `${...}` placeholders.

Expected evidence in `out/`: `token-<user>.json` (decoded claims, signature
check, discovery seen from the private network, refresh and password-grant
results), `admin.json` (roles per user, and the outcome of each fixed-id
attempt) and `summary-<mode>-<limit>.txt` (timings and memory).

## Results

Recorded on 2026-10-22 against Keycloak 26.8.1. The results and the decisions
taken from them are in ADR-027.
