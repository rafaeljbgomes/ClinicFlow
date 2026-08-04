package com.clinicflow.auth.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clinicflow.security.jwt")
public record JwtProperties(
        String issuer,
        long ttlMinutes,
        String privateKey,
        String privateKeyPath,
        String publicKey,
        String publicKeyPath
) {
}
