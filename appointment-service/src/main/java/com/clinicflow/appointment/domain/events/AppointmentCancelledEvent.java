package com.clinicflow.appointment.domain.events;

import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.CancellationActor;
import com.clinicflow.appointment.domain.CancellationReason;
import com.clinicflow.appointment.domain.DomainEventId;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;

import java.time.Instant;
import java.util.UUID;

public record AppointmentCancelledEvent(DomainEventId eventId, String eventType, Instant occurredAt,
                                        AppointmentId appointmentId, PatientId patientId,
                                        PsychologistId psychologistId, CancellationActor cancelledBy,
                                        CancellationReason reason) {
    public static AppointmentCancelledEvent of(AppointmentId appointmentId, PatientId patientId,
                                               PsychologistId psychologistId, CancellationActor cancelledBy,
                                               CancellationReason reason) {
        return new AppointmentCancelledEvent(new DomainEventId(UUID.randomUUID()), "AppointmentCancelled",
                Instant.now(), appointmentId, patientId, psychologistId, cancelledBy, reason);
    }
}
