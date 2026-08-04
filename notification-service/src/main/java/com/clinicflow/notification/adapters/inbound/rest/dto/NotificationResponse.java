package com.clinicflow.notification.adapters.inbound.rest.dto;

import com.clinicflow.notification.domain.NotificationStatus;
import com.clinicflow.notification.domain.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, UUID eventId, String eventType, UUID psychologistId,
                                   NotificationType type,
                                   NotificationStatus status, String recipient, String subject,
                                   Instant createdAt, Instant sentAt) {
}
