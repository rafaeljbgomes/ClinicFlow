package com.clinicflow.auth.infrastructure.security;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class PemUtilsTest {
    private static KeyPair pair;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        pair = keyPair();
    }

    @Test
    void readsInlinePublicKey() {
        // Arrange
        String publicPem = pem("PUBLIC KEY", pair.getPublic().getEncoded());

        // Act
        RSAPublicKey key = PemUtils.publicKey(publicPem, null);

        // Assert
        assertThat(key.getModulus()).isEqualTo(((RSAPublicKey) pair.getPublic()).getModulus());
    }

    @Test
    void readsPublicKeyFromConfiguredFile() throws Exception {
        // Arrange
        Path publicFile = Files.createTempFile("clinicflow-public", ".pem");
        Files.writeString(publicFile, pem("PUBLIC KEY", pair.getPublic().getEncoded()));

        // Act
        RSAPublicKey key = PemUtils.publicKey(null, publicFile.toString());

        // Assert
        assertThat(key.getModulus()).isEqualTo(((RSAPublicKey) pair.getPublic()).getModulus());
    }

    @Test
    void readsInlinePrivateKey() {
        // Arrange
        String privatePem = pem("PRIVATE KEY", pair.getPrivate().getEncoded());

        // Act
        RSAPrivateKey key = PemUtils.privateKey(privatePem, null);

        // Assert
        assertThat(key.getAlgorithm()).isEqualTo("RSA");
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
    void rejectsMalformedPrivateKey() {
        // Act
        Throwable thrown = catchThrowable(() -> PemUtils.privateKey("not-a-key", null));

        // Assert
        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid RSA private key configuration");
    }

    static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded)
                + "\n-----END " + type + "-----";
    }
}
