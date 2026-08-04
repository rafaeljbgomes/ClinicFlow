package com.clinicflow.clinical.adapters.inbound.messaging;

import java.time.Instant;
import java.util.UUID;

public record InboundAppointmentCompletedEvent(UUID eventId, String eventType, Instant occurredAt,
                                               UUID appointmentId, UUID patientId, UUID psychologistId,
                                               Instant appointmentDate, String appointmentType) {
}
