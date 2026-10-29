#!/bin/sh
# Creates or updates every service's PostgreSQL owner and runtime roles (ADR-026).
# Runs as the platform administrator (PGHOST/PGUSER/PGPASSWORD/PGDATABASE). Each run
# re-applies passwords, ownership and grants, so it also rotates credentials.
set -eu
. "$(dirname "$0")/credentials.sh"

retry psql -v ON_ERROR_STOP=1 -qtAc 'SELECT 1'

while read -r service database _permissions; do
    case "$service" in ''|'#'*) continue ;; esac
    (
        # Exports the service's SPRING_* variables; postgres-roles.sql reads the passwords with \getenv.
        load_credentials "$service"
        psql -v ON_ERROR_STOP=1 -q -v database="$database" -v owner="$SPRING_FLYWAY_USER" \
            -v app="$SPRING_DATASOURCE_USERNAME" -f "$bootstrap_dir/postgres-roles.sql"
        psql -v ON_ERROR_STOP=1 -q -d "$database" -v owner="$SPRING_FLYWAY_USER" \
            -v app="$SPRING_DATASOURCE_USERNAME" -f "$bootstrap_dir/postgres-grants.sql"
    )
    echo "PostgreSQL roles ready for $service ($database)."
done < "$identities_file"

# Keycloak's one login owns its database (ADR-027). Its credentials arrive as KC_DB_* only where
# Keycloak runs: Compose's env_file, or the platform chart's keycloak.databaseSecret.
if [ -n "${KC_DB_USERNAME:-}" ]; then
    printenv KC_DB_PASSWORD >/dev/null || { echo "KC_DB_USERNAME is set without its password." >&2; exit 1; }
    psql -v ON_ERROR_STOP=1 -q -f "$bootstrap_dir/keycloak-database.sql"
    echo "PostgreSQL login ready for keycloak (clinicflow_keycloak)."
fi
