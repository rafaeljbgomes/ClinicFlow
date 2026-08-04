package com.clinicflow.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class Notification {
    private final NotificationId id;
    private final EventId eventId;
    private final EventType eventType;
    private final PsychologistId psychologistId;
    private final NotificationType type;
    private final NotificationStatus status;
    private final RecipientAddress recipient;
    private final NotificationSubject subject;
    private final NotificationMessage message;
    private final FailureReason failureReason;
    private final Instant createdAt;
    private final Instant sentAt;

    private Notification(NotificationId id, EventId eventId, EventType eventType, PsychologistId psychologistId,
                         NotificationType type,
                         NotificationStatus status, RecipientAddress recipient, NotificationSubject subject,
                         NotificationMessage message, FailureReason failureReason, Instant createdAt, Instant sentAt) {
        if (id == null || eventId == null || eventType == null || type == null || status == null
                || subject == null || message == null || createdAt == null) {
            throw new IllegalArgumentException("Notification required fields are missing");
        }
        this.id = id;
        this.eventId = eventId;
        this.eventType = eventType;
        this.psychologistId = psychologistId;
        this.type = type;
        this.status = status;
        this.recipient = recipient;
        this.subject = subject;
        this.message = message;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.sentAt = sentAt;
    }

    public static Notification sent(NotificationId id, EventId eventId, EventType eventType,
                                    PsychologistId psychologistId, RecipientAddress recipient, NotificationSubject subject,
                                    NotificationMessage message, Instant now) {
        if (psychologistId == null) {
            throw new IllegalArgumentException("Notification owner is required");
        }
        return new Notification(id, eventId, eventType, psychologistId, NotificationType.EMAIL, NotificationStatus.SENT,
                recipient, subject, message, null, now, now);
    }

    public static Notification rehydrate(NotificationId id, EventId eventId, EventType eventType,
                                         PsychologistId psychologistId, NotificationType type, NotificationStatus status,
                                         RecipientAddress recipient, NotificationSubject subject,
                                         NotificationMessage message, FailureReason failureReason,
                                         Instant createdAt, Instant sentAt) {
        return new Notification(id, eventId, eventType, psychologistId, type, status, recipient, subject, message,
                failureReason, createdAt, sentAt);
    }

    public NotificationId id() { return new NotificationId(id.value()); }
    public EventId eventId() { return new EventId(eventId.value()); }
    public EventType eventType() { return new EventType(eventType.value()); }
    public Optional<PsychologistId> psychologistId() {
        return Optional.ofNullable(psychologistId).map(value -> new PsychologistId(value.value()));
    }
    public NotificationType type() { return type; }
    public NotificationStatus status() { return status; }
    public Optional<RecipientAddress> recipient() {
        return Optional.ofNullable(recipient).map(value -> new RecipientAddress(value.value()));
    }
    public NotificationSubject subject() { return new NotificationSubject(subject.value()); }
    public NotificationMessage message() { return new NotificationMessage(message.value()); }
    public Optional<FailureReason> failureReason() {
        return Optional.ofNullable(failureReason).map(value -> new FailureReason(value.value()));
    }
    public Instant createdAt() { return createdAt; }
    public Instant sentAt() { return sentAt; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Notification notification && id.equals(notification.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
