package com.clinicflow.appointment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class Appointment {
    private final AppointmentId id;
    private final PsychologistId psychologistId;
    private final PatientId patientId;
    private AppointmentDate scheduledAt;
    private AppointmentStatus status;
    private final AppointmentType type;
    private CancellationReason cancellationReason;
    private final Instant createdAt;
    private Instant updatedAt;

    private Appointment(AppointmentId id, PsychologistId psychologistId, PatientId patientId,
                        AppointmentDate scheduledAt, AppointmentStatus status, AppointmentType type,
                        CancellationReason cancellationReason, Instant createdAt, Instant updatedAt) {
        if (id == null || psychologistId == null || patientId == null || scheduledAt == null
                || status == null || type == null || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("Appointment required fields are missing");
        }
        this.id = id;
        this.psychologistId = psychologistId;
        this.patientId = patientId;
        this.scheduledAt = scheduledAt;
        this.status = status;
        this.type = type;
        this.cancellationReason = cancellationReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Appointment create(AppointmentId id, PsychologistId psychologistId, PatientId patientId,
                                     AppointmentDate scheduledAt, AppointmentType type, Instant now) {
        scheduledAt.requireFutureFrom(now);
        return new Appointment(id, psychologistId, patientId, scheduledAt, AppointmentStatus.SCHEDULED,
                type, null, now, now);
    }

    public static Appointment rehydrate(AppointmentId id, PsychologistId psychologistId, PatientId patientId,
                                        AppointmentDate scheduledAt, AppointmentStatus status, AppointmentType type,
                                        CancellationReason cancellationReason, Instant createdAt, Instant updatedAt) {
        return new Appointment(id, psychologistId, patientId, scheduledAt, status, type,
                cancellationReason, createdAt, updatedAt);
    }

    public Appointment reschedule(AppointmentDate newDate, Instant now) {
        ensureMutable();
        requireUpdateTime(now);
        if (newDate == null) {
            throw new IllegalArgumentException("Appointment date is required");
        }
        newDate.requireFutureFrom(now);
        this.scheduledAt = newDate;
        this.status = AppointmentStatus.RESCHEDULED;
        this.cancellationReason = null;
        this.updatedAt = now;
        return this;
    }

    public Appointment cancel(CancellationReason reason, Instant now) {
        ensureMutable();
        requireUpdateTime(now);
        if (reason == null) {
            throw new IllegalArgumentException("Cancellation reason is required");
        }
        this.status = AppointmentStatus.CANCELLED;
        this.cancellationReason = reason;
        this.updatedAt = now;
        return this;
    }

    public Appointment complete(Instant now) {
        ensureMutable();
        requireUpdateTime(now);
        this.status = AppointmentStatus.COMPLETED;
        this.updatedAt = now;
        return this;
    }

    private void ensureMutable() {
        if (status == AppointmentStatus.CANCELLED || status == AppointmentStatus.COMPLETED) {
            throw new IllegalStateException("Appointment can no longer be changed");
        }
    }

    private void requireUpdateTime(Instant now) {
        if (now == null || now.isBefore(createdAt)) {
            throw new IllegalArgumentException("Appointment update time is invalid");
        }
    }

    public AppointmentId id() { return new AppointmentId(id.value()); }
    public PsychologistId psychologistId() { return new PsychologistId(psychologistId.value()); }
    public PatientId patientId() { return new PatientId(patientId.value()); }
    public AppointmentDate scheduledAt() { return new AppointmentDate(scheduledAt.value()); }
    public AppointmentStatus status() { return status; }
    public AppointmentType type() { return type; }
    public Optional<CancellationReason> cancellationReason() {
        return Optional.ofNullable(cancellationReason).map(value -> new CancellationReason(value.value()));
    }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Appointment appointment && id.equals(appointment.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
