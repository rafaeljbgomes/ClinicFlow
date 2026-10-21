package com.clinicflow.clinical.infrastructure.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "clinicflow.services")
public record ServiceClientProperties(Endpoint patient, Endpoint appointment, Duration connectTimeout,
                                      Duration readTimeout) {
    public ServiceClientProperties {
        requireBaseUrl("patient", patient);
        requireBaseUrl("appointment", appointment);
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(1) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(2) : readTimeout;
    }

    private static void requireBaseUrl(String name, Endpoint endpoint) {
        if (endpoint == null || endpoint.baseUrl() == null || endpoint.baseUrl().isBlank()) {
            throw new IllegalArgumentException("clinicflow.services." + name + ".base-url is required");
        }
    }

    public record Endpoint(String baseUrl) {
    }
}
