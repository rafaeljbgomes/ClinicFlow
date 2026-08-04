package com.clinicflow.patient.domain;

import java.util.UUID;

public record PsychologistId(UUID value) {
    public PsychologistId {
        if (value == null) {
            throw new IllegalArgumentException("Psychologist id is required");
        }
    }
}
