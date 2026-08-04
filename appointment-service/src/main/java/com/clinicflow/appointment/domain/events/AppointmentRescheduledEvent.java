package com.clinicflow.appointment.domain.events;

import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.DomainEventId;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;

import java.time.Instant;
import java.util.UUID;

public record AppointmentRescheduledEvent(DomainEventId eventId, String eventType, Instant occurredAt,
                                          AppointmentId appointmentId, PatientId patientId,
                                          PsychologistId psychologistId, AppointmentDate previousDate,
                                          AppointmentDate newDate) {
    public static AppointmentRescheduledEvent of(AppointmentId appointmentId, PatientId patientId,
                                                 PsychologistId psychologistId, AppointmentDate previousDate,
                                                 AppointmentDate newDate) {
        return new AppointmentRescheduledEvent(new DomainEventId(UUID.randomUUID()), "AppointmentRescheduled",
                Instant.now(), appointmentId, patientId, psychologistId, previousDate, newDate);
    }
}
