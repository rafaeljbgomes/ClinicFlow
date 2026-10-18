package com.clinicflow.patient.adapters.outbound.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "clinicflow.outbox")
public record OutboxProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("1s") Duration pollInterval,
        @DefaultValue("20") int batchSize,
        @DefaultValue("5s") Duration confirmTimeout,
        @DefaultValue("1s") Duration retryInitial,
        @DefaultValue("1m") Duration retryMax,
        @DefaultValue("1h") Duration cleanupInterval,
        @DefaultValue("7d") Duration publishedRetention
) {
    public OutboxProperties {
        requirePositive(pollInterval, "pollInterval");
        requirePositive(confirmTimeout, "confirmTimeout");
        requirePositive(retryInitial, "retryInitial");
        requirePositive(retryMax, "retryMax");
        requirePositive(cleanupInterval, "cleanupInterval");
        requirePositive(publishedRetention, "publishedRetention");
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (retryInitial.compareTo(retryMax) > 0) {
            throw new IllegalArgumentException("retryInitial must not exceed retryMax");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
