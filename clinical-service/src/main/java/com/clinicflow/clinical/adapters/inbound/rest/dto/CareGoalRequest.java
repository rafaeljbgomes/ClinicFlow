package com.clinicflow.clinical.adapters.inbound.rest.dto;

import com.clinicflow.clinical.domain.GoalStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CareGoalRequest(UUID id, @NotBlank @Size(max = 500) String description,
                              LocalDate targetDate, GoalStatus status,
                              @Min(0) @Max(100) int progressPercentage) {
}
