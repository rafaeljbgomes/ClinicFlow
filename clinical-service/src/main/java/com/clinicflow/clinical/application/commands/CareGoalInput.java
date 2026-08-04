package com.clinicflow.clinical.application.commands;

import com.clinicflow.clinical.domain.GoalStatus;

import java.time.LocalDate;
import java.util.UUID;

public record CareGoalInput(UUID id, String description, LocalDate targetDate,
                            GoalStatus status, int progressPercentage) {
}
