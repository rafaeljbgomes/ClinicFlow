package com.clinicflow.patient.adapters.outbound.messaging;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JpaOutboxEventTest {
    @Test
    void recordsRetryableFailureAndThenPublication() {
        Instant createdAt = Instant.parse("2026-06-01T10:00:00Z");
        JpaOutboxEvent event = JpaOutboxEvent.pending(
                UUID.randomUUID(), "PatientCreated", "patient.created", "{}", null, createdAt);
        Instant retryAt = createdAt.plusSeconds(1);

        event.markFailed("broker\n unavailable", retryAt);

        assertThat(event.id()).isNotNull();
        assertThat(event.status()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.retryCount()).isOne();
        assertThat(event.nextAttemptAt()).isEqualTo(retryAt);
        assertThat(event.lastError()).isEqualTo("broker unavailable");
        assertThat(event.createdAt()).isEqualTo(createdAt);

        Instant publishedAt = createdAt.plusSeconds(2);
        event.markPublished(publishedAt);

        assertThat(event.status()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.publishedAt()).isEqualTo(publishedAt);
        assertThat(event.lastError()).isNull();
    }

    @Test
    void boundsAndDefaultsFailureDetails() {
        JpaOutboxEvent event = JpaOutboxEvent.pending(
                UUID.randomUUID(), "PatientCreated", "patient.created", "{}", "correlation", Instant.now());

        event.markFailed(" ", Instant.now());
        assertThat(event.lastError()).isEqualTo("Unknown publication failure");

        event.markFailed("x".repeat(1200), Instant.now());
        assertThat(event.lastError()).hasSize(1000);
        assertThat(event.correlationId()).isEqualTo("correlation");
    }
}
