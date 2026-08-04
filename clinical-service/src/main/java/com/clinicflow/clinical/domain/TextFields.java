package com.clinicflow.clinical.domain;

final class TextFields {
    private TextFields() {
    }

    static String required(String value, int maxLength, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return optional(value, maxLength, message);
    }

    static String optional(String value, int maxLength, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
