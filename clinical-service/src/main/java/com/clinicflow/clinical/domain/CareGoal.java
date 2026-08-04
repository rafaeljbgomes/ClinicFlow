package com.clinicflow.clinical.domain;

import java.time.LocalDate;
import java.util.Objects;

public final class CareGoal {
    private final CareGoalId id;
    private final String description;
    private final LocalDate targetDate;
    private final GoalStatus status;
    private final int progressPercentage;

    public CareGoal(CareGoalId id, String description, LocalDate targetDate,
                    GoalStatus status, int progressPercentage) {
        if (id == null || status == null) {
            throw new IllegalArgumentException("Care goal required fields are missing");
        }
        if (progressPercentage < 0 || progressPercentage > 100) {
            throw new IllegalArgumentException("Care goal progress is invalid");
        }
        this.id = id;
        this.description = TextFields.required(description, 500, "Care goal description is invalid");
        this.targetDate = targetDate;
        this.status = status;
        this.progressPercentage = progressPercentage;
    }

    public CareGoalId id() { return new CareGoalId(id.value()); }
    public String description() { return description; }
    public LocalDate targetDate() { return targetDate; }
    public GoalStatus status() { return status; }
    public int progressPercentage() { return progressPercentage; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof CareGoal goal && id.equals(goal.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
