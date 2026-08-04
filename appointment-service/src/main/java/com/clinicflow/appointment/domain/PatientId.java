package com.clinicflow.appointment.domain;

import java.util.UUID;

public record PatientId(UUID value) {
    public PatientId {
        if (value == null) {
            throw new IllegalArgumentException("Patient id is required");
        }
    }
}
