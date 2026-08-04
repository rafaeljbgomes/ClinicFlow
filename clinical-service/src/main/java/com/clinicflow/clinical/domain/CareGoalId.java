package com.clinicflow.clinical.domain;

import java.util.UUID;

public record CareGoalId(UUID value) {
    public CareGoalId {
        if (value == null) {
            throw new IllegalArgumentException("Care goal id is required");
        }
    }
}
