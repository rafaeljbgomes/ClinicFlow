package com.clinicflow.auth.domain;

public record FullName(String value) {
    public FullName {
        value = value == null ? null : value.trim();
        if (value == null || value.length() < 2 || value.length() > 160) {
            throw new IllegalArgumentException("Full name must contain between 2 and 160 characters");
        }
    }
}
