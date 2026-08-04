package com.clinicflow.notification.application.exceptions;

public class NotificationNotFoundException extends RuntimeException {
    public NotificationNotFoundException() {
        super("Notification not found");
    }
}
