package com.clinicflow.patient.adapters.outbound.messaging;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class OutboxPropertiesTest {
    @Test
    void rejectsNonPositivePollInterval() {
        // Act
        Throwable thrown = catchThrowable(() -> new OutboxProperties(
                true, Duration.ZERO, 20, Duration.ofSeconds(5),
                Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofHours(1), Duration.ofDays(7)));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pollInterval");
    }

    @Test
    void rejectsInitialRetryLongerThanMaximumRetry() {
        // Act
        Throwable thrown = catchThrowable(() -> properties(Duration.ofSeconds(2), Duration.ofSeconds(1), 20));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("retryInitial");
    }

    @Test
    void rejectsNonPositiveBatchSize() {
        // Act
        Throwable thrown = catchThrowable(() -> properties(Duration.ofSeconds(1), Duration.ofSeconds(2), 0));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("batchSize");
    }

    private OutboxProperties properties(Duration retryInitial, Duration retryMax, int batchSize) {
        return new OutboxProperties(true, Duration.ofSeconds(1), batchSize, Duration.ofSeconds(5),
                retryInitial, retryMax, Duration.ofHours(1), Duration.ofDays(7));
    }
}
