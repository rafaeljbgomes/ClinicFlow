-- Optimistic concurrency (ADR-024): every update must name the version it was based on.
-- Existing rows start at version 0, the value Hibernate assigns to a newly persisted row.
-- care_goals has no version: the care plan's version covers the whole plan, goals included.
ALTER TABLE clinical_cases
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE care_plans
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE practice_profiles
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Adding a column rewrites no signed row: the ADR-019 trigger fires on UPDATE, not on ALTER TABLE.
ALTER TABLE session_records
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
