-- Optimistic concurrency (ADR-024): every update must name the version it was based on.
-- Existing rows start at version 0, the value Hibernate assigns to a newly persisted row.
ALTER TABLE patients
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
