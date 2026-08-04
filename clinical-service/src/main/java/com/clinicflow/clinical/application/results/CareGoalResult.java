package com.clinicflow.clinical.application.results;

import com.clinicflow.clinical.domain.GoalStatus;

import java.time.LocalDate;
import java.util.UUID;

public record CareGoalResult(UUID id, String description, LocalDate targetDate,
                             GoalStatus status, int progressPercentage) {
}
