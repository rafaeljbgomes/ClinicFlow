package com.clinicflow.appointment.adapters.inbound.rest.dto;

import com.clinicflow.appointment.domain.AppointmentStatus;
import com.clinicflow.appointment.domain.AppointmentType;

import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(UUID id, UUID psychologistId, UUID patientId, Instant scheduledAt,
                                  AppointmentStatus status, AppointmentType type, String cancellationReason) {
}
