package com.clinicflow.clinical.domain;

import java.time.Instant;
import java.util.Objects;

public final class ClinicalCase {
    private final ClinicalCaseId id;
    private final PsychologistId psychologistId;
    private final PatientId patientId;
    private String presentingConcern;
    private ClinicalCaseStatus status;
    private final Instant openedAt;
    private Instant closedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private ClinicalCase(ClinicalCaseId id, PsychologistId psychologistId, PatientId patientId,
                         String presentingConcern, ClinicalCaseStatus status, Instant openedAt,
                         Instant closedAt, Instant createdAt, Instant updatedAt) {
        if (id == null || psychologistId == null || patientId == null || status == null
                || openedAt == null || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("Clinical case required fields are missing");
        }
        this.id = id;
        this.psychologistId = psychologistId;
        this.patientId = patientId;
        this.presentingConcern = TextFields.required(presentingConcern, 1000, "Presenting concern is invalid");
        this.status = status;
        this.openedAt = openedAt;
        this.closedAt = closedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ClinicalCase create(ClinicalCaseId id, PsychologistId psychologistId, PatientId patientId,
                                      String presentingConcern, Instant now) {
        return new ClinicalCase(id, psychologistId, patientId, presentingConcern,
                ClinicalCaseStatus.INTAKE, now, null, now, now);
    }

    public static ClinicalCase rehydrate(ClinicalCaseId id, PsychologistId psychologistId, PatientId patientId,
                                         String presentingConcern, ClinicalCaseStatus status, Instant openedAt,
                                         Instant closedAt, Instant createdAt, Instant updatedAt) {
        return new ClinicalCase(id, psychologistId, patientId, presentingConcern, status, openedAt,
                closedAt, createdAt, updatedAt);
    }

    public ClinicalCase changeStatus(ClinicalCaseStatus newStatus, Instant now) {
        if (newStatus == null || now == null || now.isBefore(createdAt)) {
            throw new IllegalArgumentException("Clinical case status update is invalid");
        }
        this.status = newStatus;
        this.closedAt = newStatus == ClinicalCaseStatus.DISCHARGED ? now : null;
        this.updatedAt = now;
        return this;
    }

    public ClinicalCaseId id() { return new ClinicalCaseId(id.value()); }
    public PsychologistId psychologistId() { return new PsychologistId(psychologistId.value()); }
    public PatientId patientId() { return new PatientId(patientId.value()); }
    public String presentingConcern() { return presentingConcern; }
    public ClinicalCaseStatus status() { return status; }
    public Instant openedAt() { return openedAt; }
    public Instant closedAt() { return closedAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ClinicalCase clinicalCase && id.equals(clinicalCase.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
