package com.clinicflow.notification.infrastructure.messaging;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("clinicflow.messaging.retry")
public record MessagingRetryProperties(
        @Min(1) int maxAttempts,
        @NotNull Duration initialInterval,
        @DecimalMin("1.0") double multiplier,
        @NotNull Duration maxInterval) {

    public MessagingRetryProperties {
        if (initialInterval != null && (initialInterval.isZero() || initialInterval.isNegative())) {
            throw new IllegalArgumentException("initialInterval must be positive");
        }
        if (maxInterval != null && initialInterval != null && maxInterval.compareTo(initialInterval) < 0) {
            throw new IllegalArgumentException("maxInterval must be greater than or equal to initialInterval");
        }
    }
}
