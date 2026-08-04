package com.clinicflow.notification.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PemUtilsTest {
    @Test
    void readsInlineAndFileBasedPublicKeys() throws Exception {
        String pem = publicKeyPem();
        Path file = Files.createTempFile("clinicflow-notification-public", ".pem");
        Files.writeString(file, pem);

        assertThat(PemUtils.publicKey(pem, null).getModulus())
                .isEqualTo(PemUtils.publicKey(null, file.toString()).getModulus());
    }

    @Test
    void rejectsMissingAndMalformedKeys() {
        assertThatThrownBy(() -> PemUtils.publicKey(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid RSA public key configuration");
        assertThatThrownBy(() -> PemUtils.publicKey("not-a-key", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid RSA public key configuration");
    }

    private String publicKeyPem() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var publicKey = generator.generateKeyPair().getPublic();
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(publicKey.getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}
