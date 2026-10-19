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
    private static final String EMAIL = "psychologist@example.com";
    private static final String PASSWORD = "correct-horse-battery-staple";

    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

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
    void registerCreatesAccountThroughTheRealHttpStack() {
        // Arrange
        Map<String, Object> registration = psychologistRegistration();

        // Act
        var response = http.postForEntity("/auth/register", registration, Map.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("email", EMAIL);
    }

    @Test
    void loginReturnsIssuedTokenForRegisteredCredentials() {
        // Arrange
        register(psychologistRegistration());
        Map<String, Object> credentials = Map.of("email", EMAIL, "password", PASSWORD);

        // Act
        var response = http.postForEntity("/auth/login", credentials, Map.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("accessToken", "functional-token");
    }

    @Test
    void currentUserProfileReturnsTheAuthenticatedAccount() {
        // Arrange
        String userId = register(psychologistRegistration());
        when(jwtDecoder.decode("functional-token")).thenReturn(jwt(userId, "PSYCHOLOGIST"));
        var request = new HttpEntity<>(bearer("functional-token"));

        // Act
        var response = http.exchange("/users/me", HttpMethod.GET, request, Map.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("id", userId)
                .containsEntry("email", EMAIL);
    }

    @Test
    void registerRejectsInvalidPayload() {
        // Arrange
        Map<String, Object> invalid = Map.of(
                "email", "not-an-email", "password", "short", "role", "PATIENT", "fullName", "");

        // Act
        var response = http.postForEntity("/auth/register", invalid, Map.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        // Arrange
        Map<String, Object> registration = Map.of(
                "email", "duplicate@example.com", "password", "a-secure-password", "role", "PATIENT",
                "fullName", "Patient");
        register(registration);

        // Act
        var response = http.postForEntity("/auth/register", registration, Map.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void currentUserProfileRequiresAuthentication() {
        // Act
        var response = http.getForEntity("/users/me", Map.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private Map<String, Object> psychologistRegistration() {
        return Map.of(
                "email", EMAIL,
                "password", PASSWORD,
                "role", "PSYCHOLOGIST",
                "fullName", "Dr. Functional Test");
    }

    private String register(Map<String, Object> registration) {
        return http.postForEntity("/auth/register", registration, Map.class).getBody().get("id").toString();
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
