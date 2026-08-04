CREATE TABLE patients (
    id UUID PRIMARY KEY,
    psychologist_id UUID NOT NULL,
    email VARCHAR(320) NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    phone VARCHAR(40),
    consent_status VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_patients_psychologist_id ON patients (psychologist_id);
