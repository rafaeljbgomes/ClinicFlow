-- Owner and runtime roles for one service database (ADR-026). Run by provision-postgres.sh as the
-- platform administrator on the maintenance database; every statement is idempotent.
-- Passwords come from the environment so they never appear in a process list.
\getenv owner_password SPRING_FLYWAY_PASSWORD
\getenv app_password SPRING_DATASOURCE_PASSWORD

SELECT format('CREATE ROLE %I', role_name)
FROM (VALUES (:'owner'), (:'app')) AS service_role(role_name)
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = role_name)
\gexec

-- NOINHERIT and no attributes: neither role can create roles or databases, or act as another role.
SELECT format('ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT PASSWORD %L',
              role_name, role_password)
FROM (VALUES (:'owner', :'owner_password'), (:'app', :'app_password')) AS service_role(role_name, role_password)
\gexec

SELECT format('CREATE DATABASE %I OWNER %I', :'database', :'owner')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'database')
\gexec

SELECT format('ALTER DATABASE %I OWNER TO %I', :'database', :'owner')
\gexec

-- Only the owner, the runtime role and the administrator may connect; no service reaches another database.
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', datname)
FROM pg_database
WHERE datname IN (:'database', current_database(), 'postgres')
\gexec

SELECT format('GRANT CONNECT ON DATABASE %I TO %I', :'database', :'app')
\gexec
