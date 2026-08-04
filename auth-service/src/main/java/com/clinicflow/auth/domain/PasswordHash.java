package com.clinicflow.auth.domain;

public record PasswordHash(String value) {
    public PasswordHash {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new IllegalArgumentException("Password hash is invalid");
        }
    }
}
