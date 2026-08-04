package com.clinicflow.auth.infrastructure.security;

import com.clinicflow.auth.application.ports.TokenIssuer;
import com.clinicflow.auth.domain.User;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class RsaJwtTokenIssuer implements TokenIssuer {
    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public RsaJwtTokenIssuer(JwtProperties properties) {
        RSAKey rsaKey = new RSAKey.Builder(PemUtils.publicKey(properties.publicKey(), properties.publicKeyPath()))
                .privateKey(PemUtils.privateKey(properties.privateKey(), properties.privateKeyPath()))
                .keyID(UUID.randomUUID().toString())
                .build();
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
        this.properties = properties;
    }

    @Override
    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Duration ttl = Duration.ofMinutes(properties.ttlMinutes());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .subject(user.id().value().toString())
                .claim("userId", user.id().value().toString())
                .claim("email", user.email().value())
                .claim("role", user.role().name())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return new IssuedToken(jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), ttl.toSeconds());
    }
}
