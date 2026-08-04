package com.clinicflow.clinical.domain;

import java.util.UUID;

public record ClinicalCaseId(UUID value) {
    public ClinicalCaseId {
        if (value == null) {
            throw new IllegalArgumentException("Clinical case id is required");
        }
    }
}
