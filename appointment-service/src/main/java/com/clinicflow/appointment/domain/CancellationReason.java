package com.clinicflow.appointment.domain;

public record CancellationReason(String value) {
    public CancellationReason {
        value = value == null ? null : value.trim();
        if (value == null || value.isEmpty() || value.length() > 500) {
            throw new IllegalArgumentException("Cancellation reason must contain between 1 and 500 characters");
        }
    }
}
