package com.clinicflow.appointment.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Stands in for the Keycloak realm: serves its JWKS over HTTP and signs access tokens in the ADR-027 shape, so
 * tests exercise the decoder that Spring Boot builds from the resource-server properties.
 */
final class KeycloakTokens implements AutoCloseable {
    static final String ISSUER = "http://localhost:8180/realms/clinicflow";
    static final String AUDIENCE = "clinicflow-api";
    private static final String JWKS_PATH = "/realms/clinicflow/protocol/openid-connect/certs";
    private static final String KEY_ID = "realm-signing-key";

    private final RSAKey signingKey = rsaKey();
    // Same kid as the realm key, so a token signed with it fails on the signature rather than the key lookup.
    private final RSAKey foreignKey = rsaKey();
    private final HttpServer server;

    KeycloakTokens() {
        byte[] jwks = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        server.createContext(JWKS_PATH, exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            try (var body = exchange.getResponseBody()) {
                body.write(jwks);
            }
        });
        server.start();
    }

    String jwksUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + JWKS_PATH;
    }

    JWTClaimsSet.Builder claims(UUID subject, String... roles) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject(subject.toString())
                .claim("roles", List.of(roles))
                .claim("azp", "clinicflow-bff")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)));
    }

    String sign(JWTClaimsSet.Builder claims) {
        return sign(claims, signingKey);
    }

    String signWithForeignKey(JWTClaimsSet.Builder claims) {
        return sign(claims, foreignKey);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static String sign(JWTClaimsSet.Builder claims, RSAKey key) {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).type(JOSEObjectType.JWT).build();
        SignedJWT jwt = new SignedJWT(header, claims.build());
        try {
            jwt.sign(new RSASSASigner(key));
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
        return jwt.serialize();
    }

    private static RSAKey rsaKey() {
        try {
            return new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
