package com.clinicflow.clinical.domain;

import java.util.UUID;

public record CarePlanId(UUID value) {
    public CarePlanId {
        if (value == null) {
            throw new IllegalArgumentException("Care plan id is required");
        }
    }
}
