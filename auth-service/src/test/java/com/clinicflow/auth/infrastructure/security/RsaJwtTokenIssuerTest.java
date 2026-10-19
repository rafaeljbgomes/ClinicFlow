package com.clinicflow.auth.infrastructure.security;

import com.clinicflow.auth.domain.EmailAddress;
import com.clinicflow.auth.domain.FullName;
import com.clinicflow.auth.domain.PasswordHash;
import com.clinicflow.auth.domain.Role;
import com.clinicflow.auth.domain.User;
import com.clinicflow.auth.domain.UserId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RsaJwtTokenIssuerTest {
    private static KeyPair pair;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        pair = PemUtilsTest.keyPair();
    }

    @Test
    void reportsConfiguredTtlInSeconds() {
        // Arrange
        RsaJwtTokenIssuer issuer = issuer();

        // Act
        var issued = issuer.issue(user());

        // Assert
        assertThat(issued.expiresInSeconds()).isEqualTo(1800);
    }

    @Test
    void signsRs256TokenWithUserClaims() {
        // Arrange
        RsaJwtTokenIssuer issuer = issuer();
        User user = user();

        // Act
        var issued = issuer.issue(user);

        // Assert
        Jwt jwt = NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build()
                .decode(issued.value());
        assertThat(jwt.getSubject()).isEqualTo(user.id().value().toString());
        assertThat(jwt.getClaimAsString("userId")).isEqualTo(user.id().value().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("user@example.com");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("PSYCHOLOGIST");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("clinicflow-auth");
    }

    private RsaJwtTokenIssuer issuer() {
        JwtProperties properties = new JwtProperties(
                "clinicflow-auth",
                30,
                PemUtilsTest.pem("PRIVATE KEY", pair.getPrivate().getEncoded()),
                null,
                PemUtilsTest.pem("PUBLIC KEY", pair.getPublic().getEncoded()),
                null
        );
        return new RsaJwtTokenIssuer(properties);
    }

    private User user() {
        return User.create(
                new UserId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                new EmailAddress("user@example.com"),
                new PasswordHash("encoded"),
                Role.PSYCHOLOGIST,
                new FullName("Clinic User"),
                Instant.parse("2026-06-01T10:00:00Z")
        );
    }
}
