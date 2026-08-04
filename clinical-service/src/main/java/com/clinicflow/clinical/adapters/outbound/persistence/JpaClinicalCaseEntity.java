package com.clinicflow.clinical.adapters.outbound.persistence;

import com.clinicflow.clinical.domain.ClinicalCaseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clinical_cases")
class JpaClinicalCaseEntity {
    @Id private UUID id;
    @Column(name = "psychologist_id", nullable = false) private UUID psychologistId;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(name = "presenting_concern", nullable = false, length = 1000) private String presentingConcern;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private ClinicalCaseStatus status;
    @Column(name = "opened_at", nullable = false) private Instant openedAt;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected JpaClinicalCaseEntity() {}

    JpaClinicalCaseEntity(UUID id, UUID psychologistId, UUID patientId, String presentingConcern,
                          ClinicalCaseStatus status, Instant openedAt, Instant closedAt,
                          Instant createdAt, Instant updatedAt) {
        this.id = id; this.psychologistId = psychologistId; this.patientId = patientId;
        this.presentingConcern = presentingConcern; this.status = status; this.openedAt = openedAt;
        this.closedAt = closedAt; this.createdAt = createdAt; this.updatedAt = updatedAt;
    }

    UUID id() { return id; }
    UUID psychologistId() { return psychologistId; }
    UUID patientId() { return patientId; }
    String presentingConcern() { return presentingConcern; }
    ClinicalCaseStatus status() { return status; }
    Instant openedAt() { return openedAt; }
    Instant closedAt() { return closedAt; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
}
