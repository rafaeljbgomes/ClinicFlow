package com.clinicflow.patient.adapters.outbound.messaging;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JpaOutboxEventTest {
    private static final Instant CREATED_AT = Instant.parse("2026-06-01T10:00:00Z");

    @Test
    void pendingEventKeepsItsIdentityAndCorrelation() {
        // Act
        JpaOutboxEvent event = JpaOutboxEvent.pending(
                UUID.randomUUID(), "PatientCreated", "patient.created", "{}", "correlation", CREATED_AT);

        // Assert
        assertThat(event.id()).isNotNull();
        assertThat(event.correlationId()).isEqualTo("correlation");
        assertThat(event.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void recordsRetryableFailureWithSanitizedError() {
        // Arrange
        JpaOutboxEvent event = pendingEvent();
        Instant retryAt = CREATED_AT.plusSeconds(1);

        // Act
        event.markFailed("broker\n unavailable", retryAt);

        // Assert
        assertThat(event.status()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.retryCount()).isOne();
        assertThat(event.nextAttemptAt()).isEqualTo(retryAt);
        assertThat(event.lastError()).isEqualTo("broker unavailable");
    }

    @Test
    void publicationAfterFailureClearsTheLastError() {
        // Arrange
        JpaOutboxEvent event = pendingEvent();
        event.markFailed("broker unavailable", CREATED_AT.plusSeconds(1));
        Instant publishedAt = CREATED_AT.plusSeconds(2);

        // Act
        event.markPublished(publishedAt);

        // Assert
        assertThat(event.status()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.publishedAt()).isEqualTo(publishedAt);
        assertThat(event.lastError()).isNull();
    }

    @Test
    void defaultsBlankFailureDetail() {
        // Arrange
        JpaOutboxEvent event = pendingEvent();

        // Act
        event.markFailed(" ", CREATED_AT);

        // Assert
        assertThat(event.lastError()).isEqualTo("Unknown publication failure");
    }

    @Test
    void boundsOversizedFailureDetail() {
        // Arrange
        JpaOutboxEvent event = pendingEvent();

        // Act
        event.markFailed("x".repeat(1200), CREATED_AT);

        // Assert
        assertThat(event.lastError()).hasSize(1000);
    }

    private JpaOutboxEvent pendingEvent() {
        return JpaOutboxEvent.pending(
                UUID.randomUUID(), "PatientCreated", "patient.created", "{}", null, CREATED_AT);
    }
}
