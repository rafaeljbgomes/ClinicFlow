package com.clinicflow.appointment.infrastructure.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "clinicflow.services")
public record ServiceClientProperties(Endpoint patient, Duration connectTimeout, Duration readTimeout) {
    public ServiceClientProperties {
        if (patient == null || patient.baseUrl() == null || patient.baseUrl().isBlank()) {
            throw new IllegalArgumentException("clinicflow.services.patient.base-url is required");
        }
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(1) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(2) : readTimeout;
    }

    public record Endpoint(String baseUrl) {
    }
}
