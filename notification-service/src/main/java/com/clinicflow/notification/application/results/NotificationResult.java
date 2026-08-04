package com.clinicflow.notification.application.results;

import com.clinicflow.notification.domain.NotificationStatus;
import com.clinicflow.notification.domain.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResult(UUID id, UUID eventId, String eventType, UUID psychologistId, NotificationType type,
                                 NotificationStatus status, String recipient, String subject,
                                 Instant createdAt, Instant sentAt) {
}
