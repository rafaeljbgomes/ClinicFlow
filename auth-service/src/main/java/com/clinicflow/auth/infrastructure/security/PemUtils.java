package com.clinicflow.auth.infrastructure.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

final class PemUtils {
    private PemUtils() {
    }

    static RSAPrivateKey privateKey(String pem, String path) {
        try {
            byte[] keyBytes = decode(resolve(pem, path), "PRIVATE KEY");
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid RSA private key configuration", ex);
        }
    }

    static RSAPublicKey publicKey(String pem, String path) {
        try {
            byte[] keyBytes = decode(resolve(pem, path), "PUBLIC KEY");
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid RSA public key configuration", ex);
        }
    }

    private static String resolve(String inlinePem, String path) throws IOException {
        if (inlinePem != null && !inlinePem.isBlank()) {
            return inlinePem;
        }
        if (path != null && !path.isBlank()) {
            return Files.readString(Path.of(path));
        }
        throw new IllegalStateException("RSA key must be provided inline or by path");
    }

    private static byte[] decode(String pem, String type) {
        String normalized = pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }
}
