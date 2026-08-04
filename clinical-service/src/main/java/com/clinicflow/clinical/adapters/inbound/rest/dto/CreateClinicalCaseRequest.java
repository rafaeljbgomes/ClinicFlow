package com.clinicflow.clinical.adapters.inbound.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateClinicalCaseRequest(
        @NotNull UUID patientId,
        @NotBlank @Size(max = 1000) String presentingConcern
) {
}
