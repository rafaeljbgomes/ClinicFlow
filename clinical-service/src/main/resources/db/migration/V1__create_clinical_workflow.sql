CREATE TABLE practice_profiles (
    psychologist_id UUID PRIMARY KEY,
    professional_registration VARCHAR(80),
    timezone VARCHAR(80) NOT NULL,
    default_session_duration_minutes INTEGER NOT NULL,
    primary_location VARCHAR(160),
    telehealth_enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_practice_default_duration
        CHECK (default_session_duration_minutes BETWEEN 15 AND 240)
);

CREATE TABLE clinical_cases (
    id UUID PRIMARY KEY,
    psychologist_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    presenting_concern VARCHAR(1000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_clinical_cases_open_patient
    ON clinical_cases (psychologist_id, patient_id)
    WHERE status <> 'DISCHARGED';
CREATE INDEX idx_clinical_cases_psychologist ON clinical_cases (psychologist_id);
CREATE INDEX idx_clinical_cases_patient ON clinical_cases (patient_id);
CREATE INDEX idx_clinical_cases_status ON clinical_cases (status);

CREATE TABLE care_plans (
    id UUID PRIMARY KEY,
    clinical_case_id UUID NOT NULL UNIQUE,
    psychologist_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    therapeutic_focus VARCHAR(1000) NOT NULL,
    planned_frequency VARCHAR(120) NOT NULL,
    review_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_care_plans_case FOREIGN KEY (clinical_case_id) REFERENCES clinical_cases (id) ON DELETE CASCADE
);

CREATE TABLE care_goals (
    id UUID PRIMARY KEY,
    care_plan_id UUID NOT NULL,
    description VARCHAR(500) NOT NULL,
    target_date DATE,
    status VARCHAR(32) NOT NULL,
    progress_percentage INTEGER NOT NULL,
    CONSTRAINT fk_care_goals_plan FOREIGN KEY (care_plan_id) REFERENCES care_plans (id) ON DELETE CASCADE,
    CONSTRAINT chk_care_goal_progress CHECK (progress_percentage BETWEEN 0 AND 100)
);

CREATE INDEX idx_care_goals_plan ON care_goals (care_plan_id);

CREATE TABLE session_records (
    id UUID PRIMARY KEY,
    clinical_case_id UUID,
    psychologist_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    appointment_id UUID UNIQUE,
    session_date TIMESTAMPTZ NOT NULL,
    modality VARCHAR(32) NOT NULL,
    duration_minutes INTEGER NOT NULL,
    attendance_status VARCHAR(32) NOT NULL,
    note_status VARCHAR(32) NOT NULL,
    summary VARCHAR(2000),
    focus_areas VARCHAR(1000),
    interventions VARCHAR(1000),
    homework VARCHAR(1000),
    next_steps VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_session_records_case FOREIGN KEY (clinical_case_id) REFERENCES clinical_cases (id) ON DELETE SET NULL,
    CONSTRAINT chk_session_duration CHECK (duration_minutes BETWEEN 0 AND 480)
);

CREATE INDEX idx_session_records_case ON session_records (clinical_case_id);
CREATE INDEX idx_session_records_psychologist_patient ON session_records (psychologist_id, patient_id);
CREATE INDEX idx_session_records_date ON session_records (session_date);
