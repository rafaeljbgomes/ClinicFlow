ALTER TABLE patients
    ADD COLUMN preferred_name VARCHAR(120),
    ADD COLUMN birth_date DATE,
    ADD COLUMN emergency_contact_name VARCHAR(160),
    ADD COLUMN emergency_contact_phone VARCHAR(40),
    ADD COLUMN emergency_contact_relationship VARCHAR(80),
    ADD COLUMN contact_preference VARCHAR(32) NOT NULL DEFAULT 'EMAIL',
    ADD COLUMN consent_updated_at TIMESTAMPTZ;

UPDATE patients
SET consent_updated_at = created_at
WHERE consent_updated_at IS NULL;

ALTER TABLE patients
    ALTER COLUMN consent_updated_at SET NOT NULL,
    ALTER COLUMN contact_preference DROP DEFAULT;
