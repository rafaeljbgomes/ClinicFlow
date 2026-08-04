package com.clinicflow.patient.adapters.inbound.rest.dto;

import com.clinicflow.patient.domain.PatientStatus;
import jakarta.validation.constraints.NotNull;

public record ChangePatientStatusRequest(@NotNull PatientStatus status) {
}
