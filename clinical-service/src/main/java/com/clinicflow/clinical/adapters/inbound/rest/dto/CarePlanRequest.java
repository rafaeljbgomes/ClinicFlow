package com.clinicflow.clinical.adapters.inbound.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CarePlanRequest(
        @NotBlank @Size(max = 1000) String therapeuticFocus,
        @NotBlank @Size(max = 120) String plannedFrequency,
        @NotNull LocalDate reviewDate,
        @NotEmpty List<@Valid CareGoalRequest> goals
) {
}
