package com.clinicflow.clinical.adapters.outbound.persistence;

import com.clinicflow.clinical.domain.LinkConflictSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session_record_link_conflicts")
class JpaSessionRecordLinkConflictEntity {
    @Id private UUID id;
    @Column(name = "session_record_id", nullable = false) private UUID sessionRecordId;
    @Column(name = "appointment_id", nullable = false) private UUID appointmentId;
    @Column(name = "appointment_psychologist_id", nullable = false) private UUID appointmentPsychologistId;
    @Column(name = "appointment_patient_id", nullable = false) private UUID appointmentPatientId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private LinkConflictSource source;
    @Column(name = "detected_at", nullable = false) private Instant detectedAt;

    protected JpaSessionRecordLinkConflictEntity() {}

    JpaSessionRecordLinkConflictEntity(UUID id, UUID sessionRecordId, UUID appointmentId,
                                       UUID appointmentPsychologistId, UUID appointmentPatientId,
                                       LinkConflictSource source, Instant detectedAt) {
        this.id = id; this.sessionRecordId = sessionRecordId; this.appointmentId = appointmentId;
        this.appointmentPsychologistId = appointmentPsychologistId; this.appointmentPatientId = appointmentPatientId;
        this.source = source; this.detectedAt = detectedAt;
    }

    UUID id() { return id; }
    UUID sessionRecordId() { return sessionRecordId; }
    UUID appointmentId() { return appointmentId; }
    UUID appointmentPsychologistId() { return appointmentPsychologistId; }
    UUID appointmentPatientId() { return appointmentPatientId; }
    LinkConflictSource source() { return source; }
    Instant detectedAt() { return detectedAt; }
}
