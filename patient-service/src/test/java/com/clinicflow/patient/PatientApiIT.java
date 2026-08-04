package com.clinicflow.patient;

import com.clinicflow.patient.application.ports.PatientEventPublisher;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "clinicflow.demo.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@Testcontainers
class PatientApiIT {
    private static final UUID PSYCHOLOGIST_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private PatientEventPublisher eventPublisher;

    @BeforeEach
    void cleanDatabaseAndConfigureSecurity() {
        jdbc.execute("TRUNCATE TABLE patients");
        reset(jwtDecoder, eventPublisher);
        when(jwtDecoder.decode("psychologist-token")).thenReturn(jwt(PSYCHOLOGIST_ID, "PSYCHOLOGIST"));
        when(jwtDecoder.decode("patient-token")).thenReturn(jwt(UUID.randomUUID(), "PATIENT"));
    }

    @Test
    void runsThePatientLifecycleAcrossControllerServiceAndPostgres() {
        var created = exchange("/patients", HttpMethod.POST, Map.of(
                "email", "patient@example.com",
                "fullName", "Original Name",
                "phone", "+351912345678",
                "contactPreference", "EMAIL",
                "consentStatus", "GRANTED"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = created.getBody().get("id").toString();

        var updated = exchange("/patients/" + id, HttpMethod.PUT, Map.of(
                "fullName", "Updated Name",
                "preferredName", "Updated",
                "phone", "+351912345678",
                "contactPreference", "PHONE",
                "consentStatus", "GRANTED"));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).containsEntry("fullName", "Updated Name");

        var changed = exchange("/patients/" + id + "/status", HttpMethod.PATCH, Map.of("status", "INACTIVE"));
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(changed.getBody()).containsEntry("status", "INACTIVE");

        var found = exchange("/patients/" + id, HttpMethod.GET, null);
        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody()).containsEntry("preferredName", "Updated");

        var listed = http.exchange("/patients", HttpMethod.GET,
                new HttpEntity<>(bearer("psychologist-token")), Object[].class);
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listed.getBody()).hasSize(1);
    }

    @Test
    void enforcesValidationAuthenticationAndRoleAtTheFunctionalBoundary() {
        assertThat(http.getForEntity("/patients", Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var forbidden = http.exchange("/patients", HttpMethod.GET,
                new HttpEntity<>(bearer("patient-token")), Map.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var invalid = exchange("/patients", HttpMethod.POST, Map.of(
                "email", "invalid", "fullName", "", "consentStatus", "GRANTED"));
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private org.springframework.http.ResponseEntity<Map> exchange(String path, HttpMethod method, Object body) {
        return http.exchange(path, method, new HttpEntity<>(body, bearer("psychologist-token")), Map.class);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private Jwt jwt(UUID userId, String role) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(role.toLowerCase() + "-token")
                .header("alg", "none")
                .issuedAt(now).expiresAt(now.plusSeconds(3600))
                .claim("userId", userId.toString())
                .claim("role", role)
                .build();
    }
}
