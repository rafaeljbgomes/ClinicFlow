package com.clinicflow.appointment.adapters.outbound.messaging;

import java.time.Instant;
import java.util.UUID;

public record AppointmentCancelledPayload(UUID eventId, String eventType, Instant occurredAt,
                                          UUID appointmentId, UUID patientId, UUID psychologistId,
                                          String cancelledBy, String reason) {
}
