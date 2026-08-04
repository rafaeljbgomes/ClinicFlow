package com.clinicflow.auth.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PemUtilsTest {
    @Test
    void readsInlineAndFileBasedRsaKeys() throws Exception {
        KeyPair pair = keyPair();
        String publicPem = pem("PUBLIC KEY", pair.getPublic().getEncoded());
        String privatePem = pem("PRIVATE KEY", pair.getPrivate().getEncoded());
        Path publicFile = Files.createTempFile("clinicflow-public", ".pem");
        Files.writeString(publicFile, publicPem);

        assertThat(PemUtils.publicKey(publicPem, null).getModulus())
                .isEqualTo(PemUtils.publicKey(null, publicFile.toString()).getModulus());
        assertThat(PemUtils.privateKey(privatePem, null).getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void rejectsMissingOrMalformedKeys() {
        assertThatThrownBy(() -> PemUtils.publicKey(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid RSA public key configuration");
        assertThatThrownBy(() -> PemUtils.privateKey("not-a-key", null))
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
