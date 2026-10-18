-- Signed session notes are final: record who signed and when, and reject any later change at the database level.
-- Corrections through linked amendment records are future work; until then a signed row never changes.

ALTER TABLE session_records
    ADD COLUMN signed_at TIMESTAMPTZ,
    ADD COLUMN signed_by UUID;

-- Notes signed before this migration have no recorded signature; their last update and owner are the best evidence.
UPDATE session_records
SET signed_at = updated_at,
    signed_by = psychologist_id
WHERE note_status = 'SIGNED';

ALTER TABLE session_records
    ADD CONSTRAINT chk_session_records_signature
        CHECK ((note_status = 'SIGNED') = (signed_at IS NOT NULL AND signed_by IS NOT NULL)
            AND (signed_at IS NULL) = (signed_by IS NULL));

-- SQLSTATE 23L01 is ClinicFlow's own integrity-violation subclass; the clinical service maps it to a 409 problem.
CREATE FUNCTION reject_signed_session_record_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF OLD.note_status = 'SIGNED' THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'Signed session record % cannot be deleted', OLD.id
                USING ERRCODE = '23L01', TABLE = TG_TABLE_NAME;
        END IF;
        IF NEW IS DISTINCT FROM OLD THEN
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

CREATE TRIGGER trg_session_records_lock_signed
    BEFORE UPDATE OR DELETE ON session_records
    FOR EACH ROW
EXECUTE FUNCTION reject_signed_session_record_change();
