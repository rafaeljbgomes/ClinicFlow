package com.clinicflow.notification.domain;

import java.util.UUID;

public record PsychologistId(UUID value) {
    public PsychologistId {
        if (value == null) {
            throw new IllegalArgumentException("Psychologist id is required");
        }
    }
}
