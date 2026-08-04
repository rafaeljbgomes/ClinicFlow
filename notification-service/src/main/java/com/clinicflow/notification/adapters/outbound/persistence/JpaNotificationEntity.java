package com.clinicflow.notification.adapters.outbound.persistence;

import com.clinicflow.notification.domain.NotificationStatus;
import com.clinicflow.notification.domain.NotificationType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
class JpaNotificationEntity {
    @Id private UUID id;
    @Column(name = "event_id", nullable = false, unique = true) private UUID eventId;
    @Column(name = "event_type", nullable = false, length = 80) private String eventType;
    @Column(name = "psychologist_id") private UUID psychologistId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private NotificationType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private NotificationStatus status;
    @Column(length = 320) private String recipient;
    @Column(nullable = false, length = 160) private String subject;
    @Column(nullable = false, length = 1000) private String message;
    @Column(name = "failure_reason", length = 500) private String failureReason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "sent_at") private Instant sentAt;

    protected JpaNotificationEntity() {}

    JpaNotificationEntity(UUID id, UUID eventId, String eventType, UUID psychologistId,
                          NotificationType type, NotificationStatus status,
                          String recipient, String subject, String message, String failureReason, Instant createdAt, Instant sentAt) {
        this.id = id; this.eventId = eventId; this.eventType = eventType; this.psychologistId = psychologistId;
        this.type = type; this.status = status;
        this.recipient = recipient; this.subject = subject; this.message = message; this.failureReason = failureReason;
        this.createdAt = createdAt; this.sentAt = sentAt;
    }

    UUID id() { return id; }
    UUID eventId() { return eventId; }
    String eventType() { return eventType; }
    UUID psychologistId() { return psychologistId; }
    NotificationType type() { return type; }
    NotificationStatus status() { return status; }
    String recipient() { return recipient; }
    String subject() { return subject; }
    String message() { return message; }
    String failureReason() { return failureReason; }
    Instant createdAt() { return createdAt; }
    Instant sentAt() { return sentAt; }
}
