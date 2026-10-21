# ADR-026: Per-service database and broker credentials

Status: implemented. Date: 2026-10-21. Issue: #84.

## Context

Every service connected to PostgreSQL as the superuser and to RabbitMQ as the
default administrator, both `clinicflow`/`clinicflow` and committed in Git.
ADR-004 gives each service its own database, but any service could connect to
every other database. It could read or change another service's data, drop its
Flyway history, or remove the trigger that locks signed session notes
(ADR-019). Any service could also consume, purge or delete another service's
queue and use the management API. PostgreSQL and RabbitMQ apply the
administrator password only when a volume is first initialised, so changing it
also meant losing local data.

## Decision

Each service gets its own logins with only the privileges it needs. The
administrator logins belong to the platform and are never given to a service.

### Identities

`deploy/helm/platform/files/bootstrap/service-identities.conf` lists each
service's database and RabbitMQ permissions. The bootstrap scripts, the
platform chart and the credential generator all read that file, so a new
service is added once.

| Identity | Used by | Privileges |
|---|---|---|
| `clinicflow_<service>_owner` | Flyway (`spring.flyway.user`) | Owns `clinicflow_<service>` and every object in it. No superuser, `CREATEDB`, `CREATEROLE`, replication or `BYPASSRLS`. |
| `clinicflow_<service>_app` | The runtime datasource | `CONNECT` on its own database. `SELECT/INSERT/UPDATE/DELETE` on tables and `USAGE/SELECT` on sequences, granted through default privileges for later migrations. No DDL, no `TRUNCATE`, read-only Flyway history. |
| `<service>` (RabbitMQ) | Messaging services | No tags, so no management API. Configure, write and read regexes on vhost `/` list exactly the exchanges and queues the service declares, binds, publishes to or consumes. |
| `clinicflow` | PostgreSQL, RabbitMQ and the bootstrap | Administrator. |

`CONNECT` is revoked from `PUBLIC` on every service database and on the
maintenance databases. The only roles that can connect to a service database are
its owner role, its runtime role and the administrator. The RabbitMQ patterns
follow the
[permission table](https://www.rabbitmq.com/docs/access-control). For example, a
consumer declaring its queue with a dead-letter exchange needs configure on the
queue, write on the DLX and read on the queue. Binding needs write on the queue
and read on the exchange.

### Generated credentials

`scripts/generate-dev-credentials.ps1` writes random 32-character passwords to
Git-ignored files. It keeps existing files unless `-Rotate` is passed.

- `secrets/platform/postgres.env` and `secrets/platform/rabbitmq.env`: the
  administrator logins, read only by the platform containers.
- `secrets/services/<service>.env`: `SPRING_DATASOURCE_*`, `SPRING_FLYWAY_*`
  and, for messaging services, `SPRING_RABBITMQ_*`.

Compose loads each file with `env_file`. Services mount only the JWT files they
need, not the whole `secrets/` directory. The local Kubernetes deploy creates
`clinicflow-postgres-admin`, `clinicflow-rabbitmq-admin` and
`<service>-credentials` from the same files. Each service Deployment loads its
own Secret through `envFrom`. The CI Compose entry points run the generator on
the runner before starting the stack.

### Idempotent bootstrap

`provision-postgres.sh` and `provision-rabbitmq.sh` create or update roles,
users, passwords, ownership, grants and permissions on every run. Compose runs
them in `postgres-bootstrap` and `rabbitmq-bootstrap`, and services wait for
both to complete. The platform chart runs them as a `post-install,post-upgrade`
hook Job. The Job runs non-root with a read-only root filesystem, and a
NetworkPolicy limits its egress to DNS, PostgreSQL and the RabbitMQ management
port.

On an existing volume the bootstrap hands every object in a service database to
the owner role: tables, views, sequences, routines and types. Passwords are
passed through the environment and psql's `\getenv`, so they never appear in a
process list or in SQL text built by the shell.

### Administrator rotation in place

Post-start hooks (Compose `post_start`, Kubernetes `lifecycle.postStart`)
re-apply the administrator password from the env file or Secret on every start.
PostgreSQL does it over the local socket, which the image trusts, and RabbitMQ
through `rabbitmqctl`, which the node trusts through its Erlang cookie. An
existing volume therefore picks up a new administrator password without losing
data.

In Kubernetes the deploy script also passes a SHA-256 of each credential file as
a pod-template annotation, so a changed password rolls the matching pods.

Rotation:

1. Run `scripts/generate-dev-credentials.ps1 -Rotate`.
2. Run `docker compose up -d` or `scripts/deploy-local-kubernetes.ps1`.

## Consequences

- One compromised or faulty service reaches only its own data and its own
  queues. It cannot change its schema at runtime, so it cannot drop the
  signed-note trigger.
- The runtime role can still read every table in its own database, including
  `flyway_schema_history`. Its write privileges on that table are revoked from
  the second bootstrap run onwards, because the table does not exist before the
  first migration.
- Applications still declare their own topology, so a consumer has read on the
  shared `therapy.events.exchange` and could bind its own queue to any routing
  key. Moving the topology into broker-owned definitions would remove that, at
  the cost of taking the declarations out of the services. It is deferred.
- The bootstrap passes service passwords to `rabbitmqadmin` as arguments, which
  are visible only inside the short-lived bootstrap container. The administrator
  login travels through the environment.
- Loopback connections inside the PostgreSQL container stay trusted, as the
  image configures them. Every network connection uses SCRAM-SHA-256.
- Local service runs from an IDE need the variables from
  `secrets/services/<service>.env`. The `clinicflow` defaults in
  `application.yml` no longer authenticate against the Compose database.
- Managed secret stores (External Secrets, Vault) and credentials for
  environments other than Docker Desktop remain future work. The charts already
  accept externally created Secrets.
