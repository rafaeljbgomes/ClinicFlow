package com.clinicflow.appointment;

import com.clinicflow.appointment.application.ports.AppointmentEventPublisher;
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
import java.time.temporal.ChronoUnit;
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
class AppointmentApiIT {
    private static final UUID PSYCHOLOGIST_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PATIENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private AppointmentEventPublisher eventPublisher;

    @BeforeEach
    void cleanDatabaseAndConfigureSecurity() {
        jdbc.execute("TRUNCATE TABLE appointments");
        reset(jwtDecoder, eventPublisher);
        when(jwtDecoder.decode("psychologist-token")).thenReturn(jwt("PSYCHOLOGIST"));
        when(jwtDecoder.decode("patient-token")).thenReturn(jwt("PATIENT"));
    }

    @Test
    void schedulesReschedulesAndCompletesAcrossTheRealHttpAndPersistenceStack() {
        Instant scheduledAt = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        var created = exchange("/appointments", HttpMethod.POST, Map.of(
                "patientId", PATIENT_ID.toString(),
                "scheduledAt", scheduledAt.toString(),
                "type", "ONLINE"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = created.getBody().get("id").toString();

        Instant newDate = scheduledAt.plus(1, ChronoUnit.DAYS);
        var rescheduled = exchange("/appointments/" + id + "/reschedule", HttpMethod.PATCH,
                Map.of("newDate", newDate.toString()));
        assertThat(rescheduled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rescheduled.getBody()).containsEntry("status", "RESCHEDULED")
                .containsEntry("scheduledAt", newDate.toString());

        var completed = exchange("/appointments/" + id + "/complete", HttpMethod.PATCH, null);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completed.getBody()).containsEntry("status", "COMPLETED");

        var listed = http.exchange("/appointments", HttpMethod.GET,
                new HttpEntity<>(bearer("psychologist-token")), Object[].class);
        assertThat(listed.getBody()).hasSize(1);
    }

    @Test
    void rejectsPastDatesMissingAuthenticationAndWrongRoles() {
        var invalid = exchange("/appointments", HttpMethod.POST, Map.of(
                "patientId", PATIENT_ID.toString(),
                "scheduledAt", Instant.now().minusSeconds(60).toString(),
                "type", "ONLINE"));
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.getForEntity("/appointments", Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var forbidden = http.exchange("/appointments", HttpMethod.GET,
                new HttpEntity<>(bearer("patient-token")), Map.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private org.springframework.http.ResponseEntity<Map> exchange(String path, HttpMethod method, Object body) {
        return http.exchange(path, method, new HttpEntity<>(body, bearer("psychologist-token")), Map.class);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private Jwt jwt(String role) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(role.toLowerCase() + "-token")
                .header("alg", "none")
                .issuedAt(now).expiresAt(now.plusSeconds(3600))
                .claim("userId", PSYCHOLOGIST_ID.toString())
                .claim("role", role)
                .build();
    }
}
