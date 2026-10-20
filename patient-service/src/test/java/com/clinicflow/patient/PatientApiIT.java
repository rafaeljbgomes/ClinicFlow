package com.clinicflow.patient;

import com.clinicflow.patient.application.PatientService;
import com.clinicflow.patient.application.commands.CreatePatientCommand;
import com.clinicflow.patient.domain.ConsentStatus;
import com.clinicflow.patient.domain.EmailAddress;
import com.clinicflow.patient.domain.FullName;
import com.clinicflow.patient.domain.PsychologistId;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "clinicflow.demo.enabled=false",
        "clinicflow.outbox.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@Testcontainers
class PatientApiIT {
    private static final UUID PSYCHOLOGIST_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired private RestTestClient http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PatientService patientService;
    @Autowired private TransactionTemplate transactions;
    @MockitoBean private JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanDatabaseAndConfigureSecurity() {
        jdbc.execute("TRUNCATE TABLE outbox_events, patients");
        reset(jwtDecoder);
        when(jwtDecoder.decode("psychologist-token")).thenReturn(jwt(PSYCHOLOGIST_ID, "PSYCHOLOGIST"));
        when(jwtDecoder.decode("patient-token")).thenReturn(jwt(UUID.randomUUID(), "PATIENT"));
        when(jwtDecoder.decode("other-psychologist-token")).thenReturn(jwt(UUID.randomUUID(), "PSYCHOLOGIST"));
    }

    // appointment-service and clinical-service rely on HEAD /patients/{id} as their ownership check.
    @Test
    void ownershipCheckConfirmsTheOwnersPatient() {
        // Arrange
        String patientId = createPatient();

        // Act
        var response = ownershipCheck("psychologist-token", patientId);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void ownershipCheckReturnsNoPatientDetails() {
        // Arrange
        String patientId = createPatient();

        // Act
        var response = ownershipCheck("psychologist-token", patientId);

        // Assert
        assertThat(response.getResponseBody()).isNull();
    }

    @Test
    void ownershipCheckReportsUnknownPatient() {
        // Act
        var response = ownershipCheck("psychologist-token", UUID.randomUUID().toString());

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createsPatientThroughControllerServiceAndPostgres() {
        // Arrange
        Map<String, Object> request = createRequest("patient@example.com");

        // Act
        var response = exchange("/patients", HttpMethod.POST, request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getResponseBody()).containsKey("id");
    }

    @Test
    void creationStoresOneOutboxEvent() {
        // Arrange
        Map<String, Object> request = createRequest("patient@example.com");

        // Act
        exchange("/patients", HttpMethod.POST, request);

        // Assert
        assertThat(outboxEventCount()).isOne();
    }

    @Test
    void updatesPatientProfile() {
        // Arrange
        String id = createPatient();

        // Act
        var response = exchange("/patients/" + id, HttpMethod.PUT, updateRequest());

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getResponseBody()).containsEntry("fullName", "Updated Name");
    }

    @Test
    void changesPatientStatus() {
        // Arrange
        String id = createPatient();

        // Act
        var response = exchange("/patients/" + id + "/status", HttpMethod.PATCH, Map.of("status", "INACTIVE"));

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getResponseBody()).containsEntry("status", "INACTIVE");
    }

    @Test
    void findsPersistedPatientProfile() {
        // Arrange
        String id = createPatient();
        arrangeStep("update patient", exchange("/patients/" + id, HttpMethod.PUT, updateRequest()), HttpStatus.OK);

        // Act
        var response = exchange("/patients/" + id, HttpMethod.GET, null);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getResponseBody()).containsEntry("preferredName", "Updated");
    }

    @Test
    void listsThePsychologistPatients() {
        // Arrange
        createPatient();
        var request = new HttpEntity<>(bearer("psychologist-token"));

        // Act
        var response = exchange("/patients", HttpMethod.GET, request, Object[].class);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getResponseBody()).hasSize(1);
    }

    @Test
    void rollsBackPatientAndOutboxEventTogether() {
        // Arrange
        CreatePatientCommand command = new CreatePatientCommand(
                new PsychologistId(PSYCHOLOGIST_ID),
                new EmailAddress("rollback@example.com"),
                new FullName("Rollback Patient"),
                null, null, null, null, null,
                ConsentStatus.GRANTED
        );

        // Act
        catchThrowable(() -> transactions.executeWithoutResult(status -> {
            patientService.create(command);
            throw new IllegalStateException("force rollback");
        }));

        // Assert
        assertThat(jdbc.queryForObject("SELECT count(*) FROM patients", Long.class)).isZero();
        assertThat(outboxEventCount()).isZero();
    }

    @Test
    void acceptsWriteWithOversizedCorrelationId() {
        // Arrange
        HttpEntity<Map<String, Object>> request = createRequestWithOversizedCorrelationId();

        // Act
        var response = exchange("/patients", HttpMethod.POST, request, Map.class);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void replacesOversizedCorrelationIdInResponseAndOutbox() {
        // Arrange
        HttpEntity<Map<String, Object>> request = createRequestWithOversizedCorrelationId();

        // Act
        var response = exchange("/patients", HttpMethod.POST, request, Map.class);

        // Assert
        String correlationId = response.getResponseHeaders().getFirst("X-Correlation-Id");
        assertThat(catchThrowable(() -> UUID.fromString(correlationId))).isNull();
        assertThat(jdbc.queryForObject("SELECT correlation_id FROM outbox_events", String.class))
                .isEqualTo(correlationId);
    }

    @Test
    void rejectsMissingAuthentication() {
        // Act
        var response = exchange("/patients", HttpMethod.GET, HttpEntity.EMPTY, Map.class);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsPatientRoleOnPsychologistPatients() {
        // Arrange
        var request = new HttpEntity<>(bearer("patient-token"));

        // Act
        var response = exchange("/patients", HttpMethod.GET, request, Map.class);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsInvalidPatientPayload() {
        // Arrange
        Map<String, Object> invalid = Map.of("email", "invalid", "fullName", "", "consentStatus", "GRANTED");

        // Act
        var response = exchange("/patients", HttpMethod.POST, invalid);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private String createPatient() {
        var response = exchange("/patients", HttpMethod.POST, createRequest("patient@example.com"));
        arrangeStep("create patient", response, HttpStatus.CREATED);
        return response.getResponseBody().get("id").toString();
    }

    private EntityExchangeResult<String> ownershipCheck(String token, String patientId) {
        return exchange("/patients/" + patientId, HttpMethod.HEAD, new HttpEntity<>(bearer(token)), String.class);
    }

    private static void arrangeStep(String step, ExchangeResult response, HttpStatus expected) {
        if (response.getStatus().value() != expected.value()) {
            throw new IllegalStateException(
                    "Arrange step \"" + step + "\" returned " + response.getStatus() + ", expected " + expected);
        }
    }

    private HttpEntity<Map<String, Object>> createRequestWithOversizedCorrelationId() {
        HttpHeaders headers = bearer("psychologist-token");
        headers.set("X-Correlation-Id", "a".repeat(129));
        return new HttpEntity<>(createRequest("correlation@example.com"), headers);
    }

    private Map<String, Object> createRequest(String email) {
        return Map.of(
                "email", email,
                "fullName", "Original Name",
                "phone", "+351912345678",
                "contactPreference", "EMAIL",
                "consentStatus", "GRANTED");
    }

    private Map<String, Object> updateRequest() {
        return Map.of(
                "fullName", "Updated Name",
                "preferredName", "Updated",
                "phone", "+351912345678",
                "contactPreference", "PHONE",
                "consentStatus", "GRANTED");
    }

    private Long outboxEventCount() {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class);
    }

    private EntityExchangeResult<Map> exchange(String path, HttpMethod method, Object body) {
        return exchange(path, method, new HttpEntity<>(body, bearer("psychologist-token")), Map.class);
    }

    private <T> EntityExchangeResult<T> exchange(String path, HttpMethod method, HttpEntity<?> request, Class<T> type) {
        var spec = http.method(method).uri(path).headers(headers -> headers.addAll(request.getHeaders()));
        return (request.hasBody() ? spec.body(request.getBody()) : spec).exchange().returnResult(type);
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
