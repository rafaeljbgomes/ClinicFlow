package com.clinicflow.appointment.adapters.outbound.messaging;

import com.clinicflow.appointment.domain.AppointmentType;

import java.time.Instant;
import java.util.UUID;

public record AppointmentCompletedPayload(UUID eventId, String eventType, Instant occurredAt,
                                          UUID appointmentId, UUID patientId, UUID psychologistId,
                                          Instant appointmentDate, AppointmentType appointmentType) {
}
