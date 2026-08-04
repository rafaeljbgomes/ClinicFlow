package com.clinicflow.appointment.domain.events;

import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.DomainEventId;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;

import java.time.Instant;
import java.util.UUID;

public record AppointmentScheduledEvent(DomainEventId eventId, String eventType, Instant occurredAt,
                                        AppointmentId appointmentId, PatientId patientId,
                                        PsychologistId psychologistId, AppointmentDate appointmentDate) {
    public static AppointmentScheduledEvent of(AppointmentId appointmentId, PatientId patientId,
                                               PsychologistId psychologistId, AppointmentDate appointmentDate) {
        return new AppointmentScheduledEvent(new DomainEventId(UUID.randomUUID()), "AppointmentScheduled",
                Instant.now(), appointmentId, patientId, psychologistId, appointmentDate);
    }
}
