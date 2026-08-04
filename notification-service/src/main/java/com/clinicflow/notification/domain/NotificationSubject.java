package com.clinicflow.notification.domain;

public record NotificationSubject(String value) {
    public NotificationSubject {
        value = value == null ? null : value.trim();
        if (value == null || value.isEmpty() || value.length() > 160) {
            throw new IllegalArgumentException("Notification subject is invalid");
        }
    }
}
