package com.clinicflow.auth.infrastructure.security;

import com.clinicflow.auth.domain.EmailAddress;
import com.clinicflow.auth.domain.FullName;
import com.clinicflow.auth.domain.PasswordHash;
import com.clinicflow.auth.domain.Role;
import com.clinicflow.auth.domain.User;
import com.clinicflow.auth.domain.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RsaJwtTokenIssuerTest {
    @Test
    void signsRs256TokenWithExpectedClaimsAndTtl() throws Exception {
        KeyPair pair = PemUtilsTest.keyPair();
        JwtProperties properties = new JwtProperties(
                "clinicflow-auth",
                30,
                PemUtilsTest.pem("PRIVATE KEY", pair.getPrivate().getEncoded()),
                null,
                PemUtilsTest.pem("PUBLIC KEY", pair.getPublic().getEncoded()),
                null
        );
        User user = User.create(
                new UserId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                new EmailAddress("user@example.com"),
                new PasswordHash("encoded"),
                Role.PSYCHOLOGIST,
                new FullName("Clinic User"),
                Instant.parse("2026-06-01T10:00:00Z")
        );

        var issued = new RsaJwtTokenIssuer(properties).issue(user);
        var jwt = NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build()
                .decode(issued.value());

        assertThat(issued.expiresInSeconds()).isEqualTo(1800);
        assertThat(jwt.getSubject()).isEqualTo(user.id().value().toString());
        assertThat(jwt.getClaimAsString("userId")).isEqualTo(user.id().value().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("user@example.com");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("PSYCHOLOGIST");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("clinicflow-auth");
    }
}
