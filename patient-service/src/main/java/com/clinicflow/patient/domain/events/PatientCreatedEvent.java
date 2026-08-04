package com.clinicflow.patient.domain.events;

import com.clinicflow.patient.domain.EmailAddress;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PsychologistId;

import java.time.Instant;
import java.util.UUID;

public record PatientCreatedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        PatientId patientId,
        PsychologistId psychologistId,
        EmailAddress patientEmail
) {
    public static PatientCreatedEvent of(PatientId patientId, PsychologistId psychologistId, EmailAddress patientEmail) {
        return new PatientCreatedEvent(UUID.randomUUID(), "PatientCreated", Instant.now(), patientId, psychologistId, patientEmail);
    }
}
