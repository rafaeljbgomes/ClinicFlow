package com.clinicflow.appointment.domain;

public record CancellationActor(String value) {
    public CancellationActor {
        value = value == null ? null : value.trim().toLowerCase();
        if (value == null || value.isEmpty() || value.length() > 80) {
            throw new IllegalArgumentException("Cancellation actor is invalid");
        }
    }
}
