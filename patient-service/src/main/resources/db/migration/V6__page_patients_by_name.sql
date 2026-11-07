-- The patient list pages through one psychologist's patients by name, case-insensitively, with the id as
-- tie-breaker (ADR-029). The expression matches the lower() Hibernate generates for the ignore-case sort.
CREATE INDEX idx_patients_psychologist_name
    ON patients (psychologist_id, lower(full_name), id);

-- The composite index covers every lookup the single-column one served.
DROP INDEX idx_patients_psychologist_id;
