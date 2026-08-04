package com.clinicflow.clinical.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clinicflow.security.jwt")
public record JwtProperties(String issuer, String publicKey, String publicKeyPath) {
}
