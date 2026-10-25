-- Keycloak's database and its login (ADR-026, ADR-027). Keycloak migrates its own schema on startup
-- and on every upgrade, so its one login owns the database; there is no separate runtime role.
-- Run by postgres-bootstrap as the platform administrator; every statement is idempotent.
-- The login comes from the environment so the password never appears in a process list.
\getenv keycloak_user KC_DB_USERNAME
\getenv keycloak_password KC_DB_PASSWORD

SELECT format('CREATE ROLE %I', :'keycloak_user')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'keycloak_user')
\gexec

-- NOINHERIT and no attributes: the login cannot create roles or databases, or act as another role.
SELECT format('ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT PASSWORD %L',
              :'keycloak_user', :'keycloak_password')
\gexec

SELECT format('CREATE DATABASE clinicflow_keycloak OWNER %I', :'keycloak_user')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'clinicflow_keycloak')
\gexec

SELECT format('ALTER DATABASE clinicflow_keycloak OWNER TO %I', :'keycloak_user')
\gexec

-- Only Keycloak's login and the administrator may connect.
REVOKE ALL ON DATABASE clinicflow_keycloak FROM PUBLIC;
