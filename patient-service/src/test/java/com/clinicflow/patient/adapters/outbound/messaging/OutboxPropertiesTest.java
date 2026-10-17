package com.clinicflow.patient.adapters.outbound.messaging;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxPropertiesTest {
    @Test
    void rejectsInvalidBatchAndDurations() {
        assertThatThrownBy(() -> new OutboxProperties(
                true, Duration.ZERO, 20, Duration.ofSeconds(5),
                Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofHours(1), Duration.ofDays(7)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pollInterval");
        assertThatThrownBy(() -> properties(Duration.ofSeconds(2), Duration.ofSeconds(1), 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retryInitial");
        assertThatThrownBy(() -> properties(Duration.ofSeconds(1), Duration.ofSeconds(2), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize");
    }

    private OutboxProperties properties(Duration retryInitial, Duration retryMax, int batchSize) {
        return new OutboxProperties(true, Duration.ofSeconds(1), batchSize, Duration.ofSeconds(5),
                retryInitial, retryMax, Duration.ofHours(1), Duration.ofDays(7));
    }
}
