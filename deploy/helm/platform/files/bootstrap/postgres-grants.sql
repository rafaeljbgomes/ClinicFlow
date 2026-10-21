-- Ownership and runtime privileges inside one service database (ADR-026). Run by
-- provision-postgres.sh as the platform administrator; every statement is idempotent.

-- Objects migrated before ADR-026 belong to the administrator. Hand them to the service owner,
-- so Flyway keeps migrating them and the runtime role can never alter or drop them.
SELECT format('ALTER %s %I.%I OWNER TO %I',
              CASE c.relkind WHEN 'v' THEN 'VIEW' WHEN 'm' THEN 'MATERIALIZED VIEW'
                             WHEN 'f' THEN 'FOREIGN TABLE' ELSE 'TABLE' END,
              n.nspname, c.relname, :'owner')
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relkind IN ('r', 'p', 'v', 'm', 'f')
  AND c.relowner <> :'owner'::regrole
\gexec

-- Sequences owned by a column follow their table above; this catches standalone ones.
SELECT format('ALTER SEQUENCE %I.%I OWNER TO %I', n.nspname, c.relname, :'owner')
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relkind = 'S'
  AND c.relowner <> :'owner'::regrole
\gexec

SELECT format('ALTER ROUTINE %s OWNER TO %I', p.oid::regprocedure, :'owner')
FROM pg_proc p
WHERE p.pronamespace = 'public'::regnamespace
  AND p.proowner <> :'owner'::regrole
\gexec

SELECT format(CASE t.typtype WHEN 'd' THEN 'ALTER DOMAIN %s OWNER TO %I' ELSE 'ALTER TYPE %s OWNER TO %I' END,
              t.oid::regtype, :'owner')
FROM pg_type t
LEFT JOIN pg_class c ON c.oid = t.typrelid
WHERE t.typnamespace = 'public'::regnamespace
  AND t.typowner <> :'owner'::regrole
  AND (t.typtype IN ('e', 'd') OR (t.typtype = 'c' AND c.relkind = 'c'))
\gexec

-- The runtime role reads and writes data only: no DDL, no TRUNCATE, no new objects.
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO :"app";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO :"app";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO :"app";
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner" IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app";
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner" IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO :"app";

-- Migration history is Flyway's alone. It does not exist before the first migration, so on a
-- fresh database the revocation applies from the next bootstrap run.
SELECT format('REVOKE INSERT, UPDATE, DELETE ON public.flyway_schema_history FROM %I', :'app')
WHERE to_regclass('public.flyway_schema_history') IS NOT NULL
\gexec
