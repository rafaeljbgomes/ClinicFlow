package com.clinicflow.auth;

import com.clinicflow.auth.application.ports.TokenIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
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
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "clinicflow.demo.enabled=false")
@Testcontainers
class AuthApiIT {
    private static final String EMAIL = "psychologist@example.com";
    private static final String PASSWORD = "correct-horse-battery-staple";

    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired private RestTestClient http;
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
        var response = exchange("/auth/register", HttpMethod.POST, new HttpEntity<>(registration), Map.class);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getResponseBody()).containsEntry("email", EMAIL);
    }

    @Test
    void loginReturnsIssuedTokenForRegisteredCredentials() {
        // Arrange
        register(psychologistRegistration());
        Map<String, Object> credentials = Map.of("email", EMAIL, "password", PASSWORD);

        // Act
        var response = exchange("/auth/login", HttpMethod.POST, new HttpEntity<>(credentials), Map.class);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getResponseBody()).containsEntry("accessToken", "functional-token");
    }

    @Test
    void currentUserProfileReturnsTheAuthenticatedAccount() {
        // Arrange
        String userId = register(psychologistRegistration());
        when(jwtDecoder.decode("functional-token")).thenReturn(jwt(userId, "PSYCHOLOGIST"));
        var request = new HttpEntity<>(bearer("functional-token"));

        // Act
        var response = exchange("/users/me", HttpMethod.GET, request, Map.class);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getResponseBody()).containsEntry("id", userId)
                .containsEntry("email", EMAIL);
    }

    @Test
    void registerRejectsInvalidPayload() {
        // Arrange
        Map<String, Object> invalid = Map.of(
                "email", "not-an-email", "password", "short", "role", "PATIENT", "fullName", "");

        // Act
        var response = exchange("/auth/register", HttpMethod.POST, new HttpEntity<>(invalid), Map.class);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        // Arrange
        Map<String, Object> registration = Map.of(
                "email", "duplicate@example.com", "password", "a-secure-password", "role", "PATIENT",
                "fullName", "Patient");
        register(registration);

        // Act
        var response = exchange("/auth/register", HttpMethod.POST, new HttpEntity<>(registration), Map.class);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void currentUserProfileRequiresAuthentication() {
        // Act
        var response = exchange("/users/me", HttpMethod.GET, HttpEntity.EMPTY, Map.class);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private Map<String, Object> psychologistRegistration() {
        return Map.of(
                "email", EMAIL,
                "password", PASSWORD,
                "role", "PSYCHOLOGIST",
                "fullName", "Dr. Functional Test");
    }

    private String register(Map<String, Object> registration) {
        return exchange("/auth/register", HttpMethod.POST, new HttpEntity<>(registration), Map.class)
                .getResponseBody().get("id").toString();
    }

    private <T> EntityExchangeResult<T> exchange(String path, HttpMethod method, HttpEntity<?> request, Class<T> type) {
        var spec = http.method(method).uri(path).headers(headers -> headers.addAll(request.getHeaders()));
        return (request.hasBody() ? spec.body(request.getBody()) : spec).exchange().returnResult(type);
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
