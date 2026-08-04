CREATE TABLE appointments (
    id UUID PRIMARY KEY,
    psychologist_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    type VARCHAR(32) NOT NULL,
    cancellation_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_appointments_psychologist_id ON appointments (psychologist_id);
CREATE INDEX idx_appointments_patient_id ON appointments (patient_id);
CREATE INDEX idx_appointments_scheduled_at ON appointments (scheduled_at);
