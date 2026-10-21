-- ADR-025: a session record may hold an appointment id only when it belongs to that appointment's psychologist
-- and patient. Records linked before ADR-021 can hold someone else's appointment. When the real owner shows up,
-- the clinical service records the conflict here and releases the link, so the owner's record can take it.

CREATE TABLE session_record_link_conflicts (
    id UUID PRIMARY KEY,
    session_record_id UUID NOT NULL,
    appointment_id UUID NOT NULL,
    appointment_psychologist_id UUID NOT NULL,
    appointment_patient_id UUID NOT NULL,
    source VARCHAR(32) NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_session_record_link_conflicts_record
        FOREIGN KEY (session_record_id) REFERENCES session_records (id),
    CONSTRAINT uq_session_record_link_conflicts_record_appointment UNIQUE (session_record_id, appointment_id),
    CONSTRAINT chk_session_record_link_conflicts_source CHECK (source IN ('APPOINTMENT_COMPLETED', 'MANUAL_LINK'))
);

CREATE INDEX idx_session_record_link_conflicts_appointment ON session_record_link_conflicts (appointment_id);

-- Signed rows stay final (ADR-019) with one exception: clearing an appointment link that has a conflict entry
-- above. Nothing else in the row may change in the same statement, so note content and signature are untouched.
CREATE OR REPLACE FUNCTION reject_signed_session_record_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF OLD.note_status = 'SIGNED' THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'Signed session record % cannot be deleted', OLD.id
                USING ERRCODE = '23L01', TABLE = TG_TABLE_NAME;
        END IF;
        IF NEW IS DISTINCT FROM OLD AND NOT (
                OLD.appointment_id IS NOT NULL
                AND NEW.appointment_id IS NULL
                AND to_jsonb(NEW) - 'appointment_id' = to_jsonb(OLD) - 'appointment_id'
                AND EXISTS (SELECT 1
                            FROM session_record_link_conflicts conflict
                            WHERE conflict.session_record_id = OLD.id
                              AND conflict.appointment_id = OLD.appointment_id)) THEN
            RAISE EXCEPTION 'Signed session record % cannot be changed', OLD.id
                USING ERRCODE = '23L01', TABLE = TG_TABLE_NAME;
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;
