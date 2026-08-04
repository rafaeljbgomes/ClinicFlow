package com.clinicflow.notification.infrastructure.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

final class PemUtils {
    private PemUtils() {}

    static RSAPublicKey publicKey(String pem, String path) {
        try {
            String value = pem != null && !pem.isBlank() ? pem : Files.readString(Path.of(path));
            String normalized = value.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(normalized)));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid RSA public key configuration", ex);
        }
    }
}
