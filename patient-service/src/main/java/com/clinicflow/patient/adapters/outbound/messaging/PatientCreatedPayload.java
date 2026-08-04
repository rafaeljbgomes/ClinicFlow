package com.clinicflow.patient.adapters.outbound.messaging;

import java.time.Instant;
import java.util.UUID;

public record PatientCreatedPayload(UUID eventId, String eventType, Instant occurredAt,
                                    UUID patientId, UUID psychologistId, String patientEmail) {
}
