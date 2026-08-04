package com.clinicflow.appointment.adapters.inbound.rest.dto;

import com.clinicflow.appointment.domain.AppointmentType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record ScheduleAppointmentRequest(@NotNull UUID patientId, @NotNull @Future Instant scheduledAt, @NotNull AppointmentType type) {
}
