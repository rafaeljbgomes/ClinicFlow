package com.clinicflow.auth.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtRoleConverterTest {
    private final JwtRoleConverter converter = new JwtRoleConverter();

    @Test
    void mapsRoleClaimToPrefixedAuthority() {
        // Arrange
        Jwt token = jwt(Map.of("sub", "user", "role", "ADMIN"));

        // Act
        AbstractAuthenticationToken authentication = converter.convert(token);

        // Assert
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void grantsNoAuthorityWhenRoleClaimIsMissing() {
        // Arrange
        Jwt token = jwt(Map.of("sub", "user"));

        // Act
        AbstractAuthenticationToken authentication = converter.convert(token);

        // Assert
        assertThat(authentication.getAuthorities()).isEmpty();
    }

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), claims);
    }
}
