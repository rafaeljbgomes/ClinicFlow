package com.clinicflow.auth;

import com.clinicflow.auth.application.ports.TokenIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "clinicflow.demo.enabled=false")
@Testcontainers
class AuthApiIT {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private TokenIssuer tokenIssuer;

    @BeforeEach
    void cleanDatabaseAndDoubles() {
        jdbc.execute("TRUNCATE TABLE users");
        reset(jwtDecoder, tokenIssuer);
        when(tokenIssuer.issue(any())).thenReturn(new TokenIssuer.IssuedToken("functional-token", 1800));
    }

    @Test
    void registerLoginAndReadAuthenticatedProfileThroughTheRealHttpStack() {
        Map<String, Object> registration = Map.of(
                "email", "psychologist@example.com",
                "password", "correct-horse-battery-staple",
                "role", "PSYCHOLOGIST",
                "fullName", "Dr. Functional Test");

        var created = http.postForEntity("/auth/register", registration, Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("email", "psychologist@example.com");

        var login = http.postForEntity("/auth/login", Map.of(
                "email", "psychologist@example.com",
                "password", "correct-horse-battery-staple"), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).containsEntry("accessToken", "functional-token");

        String userId = created.getBody().get("id").toString();
        when(jwtDecoder.decode("functional-token")).thenReturn(jwt(userId, "PSYCHOLOGIST"));
        var profile = http.exchange("/users/me", HttpMethod.GET,
                new HttpEntity<>(bearer("functional-token")), Map.class);

        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(profile.getBody()).containsEntry("id", userId)
                .containsEntry("email", "psychologist@example.com");
    }

    @Test
    void rejectsInvalidDuplicateAndUnauthenticatedRequests() {
        var invalid = http.postForEntity("/auth/register", Map.of(
                "email", "not-an-email", "password", "short", "role", "PATIENT", "fullName", ""), Map.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> valid = Map.of(
                "email", "duplicate@example.com", "password", "a-secure-password", "role", "PATIENT", "fullName", "Patient");
        assertThat(http.postForEntity("/auth/register", valid, Map.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(http.postForEntity("/auth/register", valid, Map.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(http.getForEntity("/users/me", Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private Jwt jwt(String userId, String role) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("functional-token")
                .header("alg", "none")
                .subject(userId)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("userId", userId)
                .claim("role", role)
                .build();
    }
}
