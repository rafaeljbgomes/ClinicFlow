-- The calendar pages through one psychologist's appointments in schedule order (ADR-029).
CREATE INDEX idx_appointments_psychologist_schedule
    ON appointments (psychologist_id, scheduled_at, id);

-- The composite index covers every lookup the single-column one served.
DROP INDEX idx_appointments_psychologist_id;
