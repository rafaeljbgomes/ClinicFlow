DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM patients
        WHERE phone IS NOT NULL
          AND phone !~ '^\+[1-9][0-9]{7,14}$'
    ) THEN
        RAISE EXCEPTION 'Cannot enforce E.164: patients.phone contains incompatible values';
    END IF;
END
$$;

ALTER TABLE patients
    ALTER COLUMN phone TYPE VARCHAR(16);

ALTER TABLE patients
    ADD CONSTRAINT chk_patients_phone_e164
        CHECK (phone IS NULL OR phone ~ '^\+[1-9][0-9]{7,14}$');
