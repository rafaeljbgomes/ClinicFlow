package com.clinicflow.notification.domain;

public record FailureReason(String value) {
    public FailureReason {
        value = value == null ? null : value.trim();
        if (value == null || value.isEmpty() || value.length() > 500) {
            throw new IllegalArgumentException("Failure reason is invalid");
        }
    }
}
