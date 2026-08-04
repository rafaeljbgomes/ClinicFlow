package com.clinicflow.appointment.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtRoleConverterTest {
    private final JwtRoleConverter converter = new JwtRoleConverter();

    @Test
    void mapsRoleClaimAndHandlesMissingRole() {
        assertThat(converter.convert(jwt(Map.of("sub", "user", "role", "PSYCHOLOGIST"))).getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_PSYCHOLOGIST");
        assertThat(converter.convert(jwt(Map.of("sub", "user"))).getAuthorities()).isEmpty();
    }

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), claims);
    }
}
