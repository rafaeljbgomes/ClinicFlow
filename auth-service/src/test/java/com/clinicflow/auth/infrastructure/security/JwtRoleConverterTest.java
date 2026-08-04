package com.clinicflow.auth.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtRoleConverterTest {
    @Test
    void mapsRoleClaimAndHandlesMissingRole() {
        JwtRoleConverter converter = new JwtRoleConverter();

        assertThat(converter.convert(jwt(Map.of("sub", "user", "role", "ADMIN"))).getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
        assertThat(converter.convert(jwt(Map.of("sub", "user"))).getAuthorities()).isEmpty();
    }

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), claims);
    }
}
