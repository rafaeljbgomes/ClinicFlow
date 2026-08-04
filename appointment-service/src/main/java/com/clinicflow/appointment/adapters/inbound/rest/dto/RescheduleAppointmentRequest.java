package com.clinicflow.appointment.adapters.inbound.rest.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RescheduleAppointmentRequest(@NotNull @Future Instant newDate) {
}
