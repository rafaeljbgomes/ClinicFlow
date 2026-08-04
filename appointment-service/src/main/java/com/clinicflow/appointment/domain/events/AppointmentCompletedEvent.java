package com.clinicflow.appointment.domain.events;

import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.AppointmentType;
import com.clinicflow.appointment.domain.DomainEventId;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;

import java.time.Instant;
import java.util.UUID;

public record AppointmentCompletedEvent(DomainEventId eventId, String eventType, Instant occurredAt,
                                        AppointmentId appointmentId, PatientId patientId,
                                        PsychologistId psychologistId, AppointmentDate appointmentDate,
                                        AppointmentType appointmentType) {
    public static AppointmentCompletedEvent of(AppointmentId appointmentId, PatientId patientId,
                                               PsychologistId psychologistId, AppointmentDate appointmentDate,
                                               AppointmentType appointmentType) {
        return new AppointmentCompletedEvent(new DomainEventId(UUID.randomUUID()), "AppointmentCompleted",
                Instant.now(), appointmentId, patientId, psychologistId, appointmentDate, appointmentType);
    }
}
