package com.clinicflow.appointment;

import com.clinicflow.appointment.application.AppointmentService;
import com.clinicflow.appointment.application.commands.ScheduleAppointmentCommand;
import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentType;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "clinicflow.demo.enabled=false",
        "clinicflow.outbox.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@Testcontainers
class AppointmentApiIT {
    private static final UUID PSYCHOLOGIST_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PATIENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AppointmentService appointmentService;
    @Autowired private TransactionTemplate transactions;
    @MockitoBean private JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanDatabaseAndConfigureSecurity() {
        jdbc.execute("TRUNCATE TABLE outbox_events, appointments");
        reset(jwtDecoder);
        when(jwtDecoder.decode("psychologist-token")).thenReturn(jwt("PSYCHOLOGIST"));
        when(jwtDecoder.decode("patient-token")).thenReturn(jwt("PATIENT"));
    }

    @Test
    void schedulesAppointmentThroughTheRealHttpAndPersistenceStack() {
        // Arrange
        Map<String, Object> request = scheduleRequest(inThreeDays());

        // Act
        var response = exchange("/appointments", HttpMethod.POST, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("id");
    }

    @Test
    void schedulingStoresOneOutboxEvent() {
        // Arrange
        Map<String, Object> request = scheduleRequest(inThreeDays());

        // Act
        exchange("/appointments", HttpMethod.POST, request);

        // Assert
        assertThat(outboxEventCount()).isOne();
    }

    @Test
    void reschedulesScheduledAppointment() {
        // Arrange
        Instant scheduledAt = inThreeDays();
        String id = scheduleAppointment(scheduledAt);
        Instant newDate = scheduledAt.plus(1, ChronoUnit.DAYS);

        // Act
        var response = exchange("/appointments/" + id + "/reschedule", HttpMethod.PATCH,
                Map.of("newDate", newDate.toString()));

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "RESCHEDULED")
                .containsEntry("scheduledAt", newDate.toString());
    }

    @Test
    void reschedulingAppendsAnOutboxEvent() {
        // Arrange
        Instant scheduledAt = inThreeDays();
        String id = scheduleAppointment(scheduledAt);

        // Act
        exchange("/appointments/" + id + "/reschedule", HttpMethod.PATCH,
                Map.of("newDate", scheduledAt.plus(1, ChronoUnit.DAYS).toString()));

        // Assert
        assertThat(outboxEventCount()).isEqualTo(2);
    }

    @Test
    void completesScheduledAppointment() {
        // Arrange
        String id = scheduleAppointment(inThreeDays());

        // Act
        var response = exchange("/appointments/" + id + "/complete", HttpMethod.PATCH, null);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "COMPLETED");
    }

    @Test
    void completionAppendsAnOutboxEvent() {
        // Arrange
        String id = scheduleAppointment(inThreeDays());

        // Act
        exchange("/appointments/" + id + "/complete", HttpMethod.PATCH, null);

        // Assert
        assertThat(outboxEventCount()).isEqualTo(2);
    }

    @Test
    void listsThePsychologistAppointments() {
        // Arrange
        scheduleAppointment(inThreeDays());
        var request = new HttpEntity<>(bearer("psychologist-token"));

        // Act
        var response = http.exchange("/appointments", HttpMethod.GET, request, Object[].class);

        // Assert
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void rollsBackAppointmentAndOutboxEventTogether() {
        // Arrange
        ScheduleAppointmentCommand command = new ScheduleAppointmentCommand(
                new PsychologistId(PSYCHOLOGIST_ID),
                new PatientId(PATIENT_ID),
                new AppointmentDate(Instant.now().plus(3, ChronoUnit.DAYS)),
                AppointmentType.ONLINE
        );

        // Act
        catchThrowable(() -> transactions.executeWithoutResult(status -> {
            appointmentService.schedule(command);
            throw new IllegalStateException("force rollback");
        }));

        // Assert
        assertThat(jdbc.queryForObject("SELECT count(*) FROM appointments", Long.class)).isZero();
        assertThat(outboxEventCount()).isZero();
    }

    @Test
    void acceptsWriteWithOversizedCorrelationId() {
        // Arrange
        HttpEntity<Map<String, Object>> request = scheduleRequestWithOversizedCorrelationId();

        // Act
        var response = http.exchange("/appointments", HttpMethod.POST, request, Map.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void replacesOversizedCorrelationIdInResponseAndOutbox() {
        // Arrange
        HttpEntity<Map<String, Object>> request = scheduleRequestWithOversizedCorrelationId();

        // Act
        var response = http.exchange("/appointments", HttpMethod.POST, request, Map.class);

        // Assert
        String correlationId = response.getHeaders().getFirst("X-Correlation-Id");
        assertThat(catchThrowable(() -> UUID.fromString(correlationId))).isNull();
        assertThat(jdbc.queryForObject("SELECT correlation_id FROM outbox_events", String.class))
                .isEqualTo(correlationId);
    }

    @Test
    void rejectsPastAppointmentDate() {
        // Arrange
        Map<String, Object> request = scheduleRequest(Instant.now().minusSeconds(60));

        // Act
        var response = exchange("/appointments", HttpMethod.POST, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsMissingAuthentication() {
        // Act
        var response = http.getForEntity("/appointments", Map.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsPatientRoleOnPsychologistAppointments() {
        // Arrange
        var request = new HttpEntity<>(bearer("patient-token"));

        // Act
        var response = http.exchange("/appointments", HttpMethod.GET, request, Map.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String scheduleAppointment(Instant scheduledAt) {
        var response = exchange("/appointments", HttpMethod.POST, scheduleRequest(scheduledAt));
        if (response.getStatusCode().value() != HttpStatus.CREATED.value()) {
            throw new IllegalStateException(
                    "Arrange step \"schedule appointment\" returned " + response.getStatusCode());
        }
        return response.getBody().get("id").toString();
    }

    private HttpEntity<Map<String, Object>> scheduleRequestWithOversizedCorrelationId() {
        HttpHeaders headers = bearer("psychologist-token");
        headers.set("X-Correlation-Id", "a".repeat(129));
        return new HttpEntity<>(scheduleRequest(inThreeDays()), headers);
    }

    private Map<String, Object> scheduleRequest(Instant scheduledAt) {
        return Map.of(
                "patientId", PATIENT_ID.toString(),
                "scheduledAt", scheduledAt.toString(),
                "type", "ONLINE");
    }

    private Instant inThreeDays() {
        return Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    }

    private Long outboxEventCount() {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class);
    }

    private ResponseEntity<Map> exchange(String path, HttpMethod method, Object body) {
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
