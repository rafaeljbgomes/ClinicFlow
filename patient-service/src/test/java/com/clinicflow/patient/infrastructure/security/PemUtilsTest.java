package com.clinicflow.patient.infrastructure.security;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class PemUtilsTest {
    private static KeyPair pair;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        pair = generator.generateKeyPair();
    }

    @Test
    void readsInlinePublicKey() {
        // Arrange
        String pem = publicKeyPem();

        // Act
        RSAPublicKey key = PemUtils.publicKey(pem, null);

        // Assert
        assertThat(key.getModulus()).isEqualTo(((RSAPublicKey) pair.getPublic()).getModulus());
    }

    @Test
    void readsPublicKeyFromConfiguredFile() throws Exception {
        // Arrange
        Path file = Files.createTempFile("clinicflow-patient-public", ".pem");
        Files.writeString(file, publicKeyPem());

        // Act
        RSAPublicKey key = PemUtils.publicKey(null, file.toString());

        // Assert
        assertThat(key.getModulus()).isEqualTo(((RSAPublicKey) pair.getPublic()).getModulus());
    }

    @Test
    void rejectsMissingPublicKeyConfiguration() {
        // Act
        Throwable thrown = catchThrowable(() -> PemUtils.publicKey(null, null));

        // Assert
        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid RSA public key configuration");
    }

    @Test
    void rejectsMalformedPublicKey() {
        // Act
        Throwable thrown = catchThrowable(() -> PemUtils.publicKey("not-a-key", null));

        // Assert
        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid RSA public key configuration");
    }

    private String publicKeyPem() {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}
