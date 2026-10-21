#!/bin/sh
# Post-start hook of the PostgreSQL container (ADR-026). The image applies POSTGRES_PASSWORD only when
# it initialises an empty volume, so this re-applies it on every start over the local socket, which
# the image trusts. Rotating the administrator password is then: change the secret, restart.
set -eu

attempt=1
until psql -v ON_ERROR_STOP=1 -q -h /var/run/postgresql -U "$POSTGRES_USER" -d postgres >/dev/null 2>&1 <<'SQL'
\getenv admin_password POSTGRES_PASSWORD
SELECT format('ALTER ROLE %I PASSWORD %L', current_user, :'admin_password')
\gexec
SQL
do
    if [ "$attempt" -ge 90 ]; then
        echo "Could not apply the PostgreSQL administrator password." >&2
        exit 1
    fi
    attempt=$((attempt + 1))
    sleep 2
done
