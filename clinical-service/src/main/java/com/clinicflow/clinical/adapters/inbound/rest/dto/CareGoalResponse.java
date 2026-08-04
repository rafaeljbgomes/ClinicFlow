package com.clinicflow.clinical.adapters.inbound.rest.dto;

import com.clinicflow.clinical.domain.GoalStatus;

import java.time.LocalDate;
import java.util.UUID;

public record CareGoalResponse(UUID id, String description, LocalDate targetDate,
                               GoalStatus status, int progressPercentage) {
}
