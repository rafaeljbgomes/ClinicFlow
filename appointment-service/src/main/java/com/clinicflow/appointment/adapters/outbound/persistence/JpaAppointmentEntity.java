package com.clinicflow.appointment.adapters.outbound.persistence;

import com.clinicflow.appointment.domain.AppointmentStatus;
import com.clinicflow.appointment.domain.AppointmentType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointments")
class JpaAppointmentEntity {
    @Id private UUID id;
    @Column(name = "psychologist_id", nullable = false) private UUID psychologistId;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(name = "scheduled_at", nullable = false) private Instant scheduledAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private AppointmentStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private AppointmentType type;
    @Column(name = "cancellation_reason", length = 500) private String cancellationReason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected JpaAppointmentEntity() {}

    JpaAppointmentEntity(UUID id, UUID psychologistId, UUID patientId, Instant scheduledAt, AppointmentStatus status,
                         AppointmentType type, String cancellationReason, Instant createdAt, Instant updatedAt) {
        this.id = id; this.psychologistId = psychologistId; this.patientId = patientId; this.scheduledAt = scheduledAt;
        this.status = status; this.type = type; this.cancellationReason = cancellationReason; this.createdAt = createdAt; this.updatedAt = updatedAt;
    }

    UUID id() { return id; }
    UUID psychologistId() { return psychologistId; }
    UUID patientId() { return patientId; }
    Instant scheduledAt() { return scheduledAt; }
    AppointmentStatus status() { return status; }
    AppointmentType type() { return type; }
    String cancellationReason() { return cancellationReason; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
}
