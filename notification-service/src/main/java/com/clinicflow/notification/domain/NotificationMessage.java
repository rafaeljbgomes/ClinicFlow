package com.clinicflow.notification.domain;

public record NotificationMessage(String value) {
    public NotificationMessage {
        value = value == null ? null : value.trim();
        if (value == null || value.isEmpty() || value.length() > 1000) {
            throw new IllegalArgumentException("Notification message is invalid");
        }
    }
}
