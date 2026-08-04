package com.clinicflow.patient.domain;

public record PreferredName(String value) {
    public PreferredName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Preferred name is required");
        }
        value = value.trim();
        if (value.length() > 120) {
            throw new IllegalArgumentException("Preferred name is too long");
        }
    }
}
