package com.clinicflow.appointment;

import com.clinicflow.appointment.application.AppointmentService;
import com.clinicflow.appointment.application.commands.CancelAppointmentCommand;
import com.clinicflow.appointment.application.commands.CompleteAppointmentCommand;
import com.clinicflow.appointment.application.commands.ScheduleAppointmentCommand;
import com.clinicflow.appointment.application.exceptions.StaleResourceVersionException;
import com.clinicflow.appointment.application.ports.AppointmentRepository;
import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.AppointmentType;
import com.clinicflow.appointment.domain.CancellationReason;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;
import com.clinicflow.appointment.support.StubHttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
class AppointmentApiIT {
    private static final UUID PSYCHOLOGIST_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PATIENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OTHER_PSYCHOLOGIST_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID FOREIGN_PATIENT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID UNVERIFIABLE_PATIENT_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    // A real HTTP stand-in for patient-service, so the outbound adapter and token forwarding run for real.
    private static final StubHttpServer PATIENT_SERVICE = StubHttpServer.start();

    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void patientService(DynamicPropertyRegistry registry) {
        registry.add("clinicflow.services.patient.base-url", PATIENT_SERVICE::baseUrl);
    }

    @AfterAll
    static void stopPatientService() {
        PATIENT_SERVICE.close();
    }

    @Autowired private RestTestClient http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AppointmentService appointmentService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private TransactionTemplate transactions;
    @MockitoBean private JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanDatabaseAndConfigureSecurity() {
        jdbc.execute("TRUNCATE TABLE outbox_events, appointments");
        reset(jwtDecoder);
        when(jwtDecoder.decode("psychologist-token")).thenReturn(jwt("PSYCHOLOGIST"));
        when(jwtDecoder.decode("patient-token")).thenReturn(jwt("PATIENT"));
        when(jwtDecoder.decode("other-psychologist-token"))
                .thenReturn(jwt("other-psychologist-token", "PSYCHOLOGIST", OTHER_PSYCHOLOGIST_ID));
        PATIENT_SERVICE.reset();
        PATIENT_SERVICE.reply("HEAD", "/patients/" + PATIENT_ID, 200);
        PATIENT_SERVICE.reply("HEAD", "/patients/" + FOREIGN_PATIENT_ID, 404);
        PATIENT_SERVICE.reply("HEAD", "/patients/" + UNVERIFIABLE_PATIENT_ID, 500);
    }

    @AfterEach
    void clearCallerContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void schedulesAppointmentThroughTheRealHttpAndPersistenceStack() {
        // Arrange
        Map<String, Object> request = scheduleRequest(inThreeDays());

        // Act
        var response = exchange("/appointments", HttpMethod.POST, request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getResponseBody()).containsKey("id");
    }

    @Test
    void schedulingAsksPatientServiceWithTheCallerToken() {
        // Arrange
        Map<String, Object> request = scheduleRequest(inThreeDays());

        // Act
        exchange("/appointments", HttpMethod.POST, request);

        // Assert
        assertThat(PATIENT_SERVICE.requests()).singleElement().satisfies(sent -> {
            assertThat(sent.method()).isEqualTo("HEAD");
            assertThat(sent.path()).isEqualTo("/patients/" + PATIENT_ID);
            assertThat(sent.authorization()).isEqualTo("Bearer psychologist-token");
        });
    }

    @Test
    void rejectsSchedulingForAnotherPsychologistsPatient() {
        // Arrange
        Map<String, Object> request = scheduleRequest(FOREIGN_PATIENT_ID, inThreeDays());

        // Act
        var response = exchange("/appointments", HttpMethod.POST, request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getResponseBody()).containsEntry("code", "patient_not_found");
    }

    @Test
    void storesNothingForAnotherPsychologistsPatient() {
        // Arrange
        Map<String, Object> request = scheduleRequest(FOREIGN_PATIENT_ID, inThreeDays());

        // Act
        exchange("/appointments", HttpMethod.POST, request);

        // Assert
        assertThat(jdbc.queryForObject("SELECT count(*) FROM appointments", Long.class)).isZero();
        assertThat(outboxEventCount()).isZero();
    }

    @Test
    void failsClosedWhenPatientServiceCannotVerifyOwnership() {
        // Arrange
        Map<String, Object> request = scheduleRequest(UNVERIFIABLE_PATIENT_ID, inThreeDays());

        // Act
        var response = exchange("/appointments", HttpMethod.POST, request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getResponseBody()).containsEntry("code", "ownership_check_unavailable");
    }

    @Test
    void anotherPsychologistsAppointmentLooksExactlyLikeAMissingOne() {
        // Arrange
        String id = scheduleAppointment(inThreeDays());
        var other = new HttpEntity<>(bearer("other-psychologist-token"));
        var missing = exchange("/appointments/" + UUID.randomUUID(), HttpMethod.GET, other, Map.class);

        // Act
        var foreign = exchange("/appointments/" + id, HttpMethod.GET, other, Map.class);

        // Assert
        assertThat(foreign.getStatus()).isEqualTo(HttpStatus.NOT_FOUND).isEqualTo(missing.getStatus());
        assertThat(withoutRequestFields(foreign.getResponseBody()))
                .isEqualTo(withoutRequestFields(missing.getResponseBody()));
    }

    @Test
    void anotherPsychologistCannotCancelTheAppointment() {
        // Arrange
        String id = scheduleAppointment(inThreeDays());
        HttpHeaders headers = bearer("other-psychologist-token");
        headers.setIfMatch("\"0\"");
        var request = new HttpEntity<>(Map.of("reason", "Probe"), headers);

        // Act
        var response = exchange("/appointments/" + id + "/cancel", HttpMethod.PATCH, request, Map.class);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT status FROM appointments WHERE id = ?::uuid", String.class, id))
                .isEqualTo("SCHEDULED");
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
                Map.of("newDate", newDate.toString()), "\"0\"");

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getResponseBody()).containsEntry("status", "RESCHEDULED")
                .containsEntry("scheduledAt", newDate.toString());
    }

    @Test
    void reschedulingAppendsAnOutboxEvent() {
        // Arrange
        Instant scheduledAt = inThreeDays();
        String id = scheduleAppointment(scheduledAt);

        // Act
        exchange("/appointments/" + id + "/reschedule", HttpMethod.PATCH,
                Map.of("newDate", scheduledAt.plus(1, ChronoUnit.DAYS).toString()), "\"0\"");

        // Assert
        assertThat(outboxEventCount()).isEqualTo(2);
    }

    @Test
    void completesScheduledAppointment() {
        // Arrange
        String id = scheduleAppointment(inThreeDays());

        // Act
        var response = exchange("/appointments/" + id + "/complete", HttpMethod.PATCH, null, "\"0\"");

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getResponseBody()).containsEntry("status", "COMPLETED").containsEntry("version", 1);
        assertThat(response.getResponseHeaders().getETag()).isEqualTo("\"1\"");
    }

    @Test
    void completionWithoutIfMatchIsPreconditionRequiredAndRecordsNothing() {
        // Arrange
        String id = scheduleAppointment(inThreeDays());

        // Act
        var response = exchange("/appointments/" + id + "/complete", HttpMethod.PATCH, null);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(outboxEventCount()).isOne();
    }

    @Test
    void staleCompletionIsPreconditionFailedAndRecordsNothing() {
        // Arrange
        Instant scheduledAt = inThreeDays();
        String id = scheduleAppointment(scheduledAt);
        var rescheduled = exchange("/appointments/" + id + "/reschedule", HttpMethod.PATCH,
                Map.of("newDate", scheduledAt.plus(1, ChronoUnit.DAYS).toString()), "\"0\"");
        if (rescheduled.getStatus().value() != HttpStatus.OK.value()) {
            throw new IllegalStateException("Arrange step \"reschedule\" returned " + rescheduled.getStatus());
        }

        // Act
        var response = exchange("/appointments/" + id + "/complete", HttpMethod.PATCH, null, "\"0\"");

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
        assertThat(response.getResponseBody()).containsEntry("code", "stale_resource_version");
        assertThat(jdbc.queryForObject("SELECT status FROM appointments WHERE id = ?::uuid", String.class, id))
                .isEqualTo("RESCHEDULED");
        assertThat(outboxEventCount()).isEqualTo(2);
    }

    // Completing and cancelling the same appointment at once must not record both outcomes: one transaction
    // wins, the other is rolled back with its outbox event, and the stored status matches the recorded event.
    @Test
    void concurrentCompleteAndCancelRecordExactlyOneOutcome() throws Exception {
        // Arrange
        UUID id = UUID.fromString(scheduleAppointment(inThreeDays()));
        AppointmentId appointmentId = new AppointmentId(id);
        PsychologistId owner = new PsychologistId(PSYCHOLOGIST_ID);

        // Act
        List<Throwable> outcomes = racingFromTheSameRead(appointmentId,
                () -> appointmentService.complete(new CompleteAppointmentCommand(owner, appointmentId, 0)),
                () -> appointmentService.cancel(new CancelAppointmentCommand(owner, appointmentId,
                        new CancellationReason("Patient cancelled"), 0)));

        // Assert
        assertThat(outcomes).filteredOn(Objects::isNull).hasSize(1);
        assertThat(outcomes).filteredOn(Objects::nonNull).singleElement()
                .isInstanceOf(StaleResourceVersionException.class);
        String status = jdbc.queryForObject("SELECT status FROM appointments WHERE id = ?", String.class, id);
        List<String> outcomeEvents = jdbc.queryForList(
                "SELECT routing_key FROM outbox_events WHERE routing_key <> 'appointment.scheduled'", String.class);
        assertThat(outcomeEvents).containsExactly(
                "COMPLETED".equals(status) ? "appointment.completed" : "appointment.cancelled");
    }

    @Test
    void completionAppendsAnOutboxEvent() {
        // Arrange
        String id = scheduleAppointment(inThreeDays());

        // Act
        exchange("/appointments/" + id + "/complete", HttpMethod.PATCH, null, "\"0\"");

        // Assert
        assertThat(outboxEventCount()).isEqualTo(2);
    }

    @Test
    void listsThePsychologistAppointments() {
        // Arrange
        scheduleAppointment(inThreeDays());
        var request = new HttpEntity<>(bearer("psychologist-token"));

        // Act
        var response = exchange("/appointments", HttpMethod.GET, request, Map.class);

        // Assert
        assertThat((List<?>) response.getResponseBody().get("content")).hasSize(1);
    }

    @Test
    void listsOnlyTheRequestedWindowInScheduleOrder() {
        // Arrange
        Instant start = inThreeDays();
        String later = scheduleAppointment(start.plus(2, ChronoUnit.HOURS));
        String earlier = scheduleAppointment(start.plus(1, ChronoUnit.HOURS));
        scheduleAppointment(start.plus(10, ChronoUnit.DAYS));
        var request = new HttpEntity<>(bearer("psychologist-token"));

        // Act
        var response = exchange("/appointments?from=" + start + "&to=" + start.plus(1, ChronoUnit.DAYS),
                HttpMethod.GET, request, Map.class);

        // Assert
        List<?> content = (List<?>) response.getResponseBody().get("content");
        assertThat(content.stream().map(item -> (Object) ((Map<?, ?>) item).get("id")).toList())
                .containsExactly(earlier, later);
        assertThat(((Map<?, ?>) response.getResponseBody().get("page")).get("totalElements")).isEqualTo(2);
    }

    @Test
    void rollsBackAppointmentAndOutboxEventTogether() {
        // Arrange
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt("PSYCHOLOGIST")));
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
        var response = exchange("/appointments", HttpMethod.POST, request, Map.class);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void replacesOversizedCorrelationIdInResponseAndOutbox() {
        // Arrange
        HttpEntity<Map<String, Object>> request = scheduleRequestWithOversizedCorrelationId();

        // Act
        var response = exchange("/appointments", HttpMethod.POST, request, Map.class);

        // Assert
        String correlationId = response.getResponseHeaders().getFirst("X-Correlation-Id");
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
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsMissingAuthentication() {
        // Act
        var response = exchange("/appointments", HttpMethod.GET, HttpEntity.EMPTY, Map.class);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsPatientRoleOnPsychologistAppointments() {
        // Arrange
        var request = new HttpEntity<>(bearer("patient-token"));

        // Act
        var response = exchange("/appointments", HttpMethod.GET, request, Map.class);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String scheduleAppointment(Instant scheduledAt) {
        var response = exchange("/appointments", HttpMethod.POST, scheduleRequest(scheduledAt));
        if (response.getStatus().value() != HttpStatus.CREATED.value()) {
            throw new IllegalStateException(
                    "Arrange step \"schedule appointment\" returned " + response.getStatus());
        }
        return response.getResponseBody().get("id").toString();
    }

    private HttpEntity<Map<String, Object>> scheduleRequestWithOversizedCorrelationId() {
        HttpHeaders headers = bearer("psychologist-token");
        headers.set("X-Correlation-Id", "a".repeat(129));
        return new HttpEntity<>(scheduleRequest(inThreeDays()), headers);
    }

    private Map<String, Object> scheduleRequest(Instant scheduledAt) {
        return scheduleRequest(PATIENT_ID, scheduledAt);
    }

    private Map<String, Object> scheduleRequest(UUID patientId, Instant scheduledAt) {
        return Map.of(
                "patientId", patientId.toString(),
                "scheduledAt", scheduledAt.toString(),
                "type", "ONLINE");
    }

    private Instant inThreeDays() {
        return Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    }

    private Long outboxEventCount() {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class);
    }

    private EntityExchangeResult<Map> exchange(String path, HttpMethod method, Object body) {
        return exchange(path, method, new HttpEntity<>(body, bearer("psychologist-token")), Map.class);
    }

    private EntityExchangeResult<Map> exchange(String path, HttpMethod method, Object body, String ifMatch) {
        HttpHeaders headers = bearer("psychologist-token");
        headers.setIfMatch(ifMatch);
        return exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    // Each writer loads the appointment in its own transaction, waits until both have read it, then runs its
    // command in that transaction, so the service sees the same snapshot and only the database can tell.
    private List<Throwable> racingFromTheSameRead(AppointmentId id, Runnable first, Runnable second)
            throws Exception {
        CyclicBarrier bothLoaded = new CyclicBarrier(2);
        ExecutorService writers = Executors.newFixedThreadPool(2);
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (Runnable writer : List.of(first, second)) {
                futures.add(writers.submit(() -> catchThrowable(() -> transactions.executeWithoutResult(status -> {
                    appointmentRepository.findById(id).orElseThrow();
                    await(bothLoaded);
                    writer.run();
                }))));
            }
            List<Throwable> outcomes = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            writers.shutdownNow();
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("Concurrent writers did not meet", ex);
        }
    }

    private <T> EntityExchangeResult<T> exchange(String path, HttpMethod method, HttpEntity<?> request, Class<T> type) {
        var spec = http.method(method).uri(path).headers(headers -> headers.addAll(request.getHeaders()));
        return (request.hasBody() ? spec.body(request.getBody()) : spec).exchange().returnResult(type);
    }

    // Everything except the per-request instance and correlation id must match.
    private static Map<?, ?> withoutRequestFields(Map<?, ?> problem) {
        Map<Object, Object> copy = new java.util.HashMap<>(problem);
        copy.remove("instance");
        copy.remove("correlationId");
        return copy;
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private Jwt jwt(String role) {
        return jwt(role.toLowerCase() + "-token", role, PSYCHOLOGIST_ID);
    }

    private Jwt jwt(String token, String role, UUID userId) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(token)
                .header("alg", "none")
                .issuedAt(now).expiresAt(now.plusSeconds(3600))
                .issuer("http://localhost:8180/realms/clinicflow")
                .audience(List.of("clinicflow-api"))
                .subject(userId.toString())
                .claim("roles", List.of(role))
                .build();
    }
}
