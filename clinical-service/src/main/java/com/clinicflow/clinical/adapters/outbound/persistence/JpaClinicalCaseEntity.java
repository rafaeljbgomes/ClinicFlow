package com.clinicflow.clinical.adapters.outbound.persistence;

import com.clinicflow.clinical.domain.ClinicalCaseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

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
    // A wrapper type, so Spring Data persists a null version and merges anything else.
    @Version @Column(nullable = false) private Long version;

    protected JpaClinicalCaseEntity() {}

    JpaClinicalCaseEntity(UUID id, UUID psychologistId, UUID patientId, String presentingConcern,
                          ClinicalCaseStatus status, Instant openedAt, Instant closedAt,
                          Instant createdAt, Instant updatedAt, Long version) {
        this.id = id; this.psychologistId = psychologistId; this.patientId = patientId;
        this.presentingConcern = presentingConcern; this.status = status; this.openedAt = openedAt;
        this.closedAt = closedAt; this.createdAt = createdAt; this.updatedAt = updatedAt;
        this.version = version;
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
    Long version() { return version; }
}
