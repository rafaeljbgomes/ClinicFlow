package com.clinicflow.appointment.adapters.outbound.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
class JpaOutboxEvent {
    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    private UUID id;
    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;
    @Column(name = "routing_key", nullable = false, length = 160)
    private String routingKey;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;
    @Column(name = "correlation_id", length = 128)
    private String correlationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OutboxEventStatus status;
    @Column(name = "retry_count", nullable = false)
    private int retryCount;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    protected JpaOutboxEvent() {
    }

    private JpaOutboxEvent(UUID id, UUID eventId, String eventType, String routingKey, String payload,
                           String correlationId, Instant createdAt) {
        this.id = id;
        this.eventId = eventId;
        this.eventType = eventType;
        this.routingKey = routingKey;
        this.payload = payload;
        this.correlationId = correlationId;
        this.status = OutboxEventStatus.PENDING;
        this.retryCount = 0;
        this.nextAttemptAt = createdAt;
        this.createdAt = createdAt;
    }

    static JpaOutboxEvent pending(UUID eventId, String eventType, String routingKey, String payload,
                                  String correlationId, Instant createdAt) {
        return new JpaOutboxEvent(UUID.randomUUID(), eventId, eventType, routingKey, payload,
                correlationId, createdAt);
    }

    void markPublished(Instant publishedAt) {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        this.lastError = null;
    }

    void markFailed(String error, Instant nextAttemptAt) {
        this.status = OutboxEventStatus.FAILED;
        this.retryCount++;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = sanitize(error);
    }

    private String sanitize(String error) {
        if (error == null || error.isBlank()) {
            return "Unknown publication failure";
        }
        String singleLine = error.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= MAX_ERROR_LENGTH
                ? singleLine : singleLine.substring(0, MAX_ERROR_LENGTH);
    }

    UUID id() { return id; }
    UUID eventId() { return eventId; }
    String eventType() { return eventType; }
    String routingKey() { return routingKey; }
    String payload() { return payload; }
    String correlationId() { return correlationId; }
    OutboxEventStatus status() { return status; }
    int retryCount() { return retryCount; }
    Instant nextAttemptAt() { return nextAttemptAt; }
    Instant createdAt() { return createdAt; }
    Instant publishedAt() { return publishedAt; }
    String lastError() { return lastError; }
}
