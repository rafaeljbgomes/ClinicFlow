-- The case list pages through one psychologist's cases, most recently opened first (ADR-029).
CREATE INDEX idx_clinical_cases_psychologist_opened
    ON clinical_cases (psychologist_id, opened_at DESC, id DESC);

-- The composite index covers every lookup the single-column one served.
DROP INDEX idx_clinical_cases_psychologist;

-- A case's session records page latest session first, and the index still serves the case foreign key.
CREATE INDEX idx_session_records_case_date
    ON session_records (clinical_case_id, session_date DESC, id DESC);

DROP INDEX idx_session_records_case;
