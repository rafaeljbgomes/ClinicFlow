package com.clinicflow.clinical;

import com.clinicflow.clinical.application.ports.SessionRecordRepository;
import com.clinicflow.clinical.application.ClinicalService;
import com.clinicflow.clinical.domain.AppointmentId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "clinicflow.demo.enabled=false")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AppointmentCompletedMessagingIT {
    private static final String EXCHANGE = "therapy.events.exchange";
    private static final String DLQ = "clinical.appointment-events.dlq";

    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    @Container
    @ServiceConnection(name = "rabbitmq")
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(
            org.testcontainers.utility.DockerImageName
                    .parse("rabbitmq:4.3.4-management-alpine@sha256:44bf7eb50fe1765885659e49ccfdc775f8e531964d979321aee380a071f49f94")
                    .asCompatibleSubstituteFor("rabbitmq"));

    @Autowired private RabbitTemplate rabbit;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SessionRecordRepository sessions;
    @MockitoSpyBean private ClinicalService clinicalService;
    @MockitoBean private JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE session_records, care_goals, care_plans, clinical_cases, practice_profiles CASCADE");
    }

    @Test
    void completedAppointmentCreatesSessionWithTheAppointmentModality() {
        // Arrange
        UUID appointmentId = UUID.randomUUID();
        Map<String, Object> event = appointmentCompleted(appointmentId);

        // Act
        rabbit.convertAndSend(EXCHANGE, "appointment.completed", event);

        // Assert
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(sessions.findByAppointmentId(new AppointmentId(appointmentId)))
                        .hasValueSatisfying(session -> assertThat(session.modality().name()).isEqualTo("ONLINE")));
    }

    @Test
    void redeliveredCompletedAppointmentCreatesOnlyOneSession() {
        // Arrange
        UUID appointmentId = UUID.randomUUID();
        Map<String, Object> event = appointmentCompleted(appointmentId);

        // Act
        rabbit.convertAndSend(EXCHANGE, "appointment.completed", event);
        rabbit.convertAndSend(EXCHANGE, "appointment.completed", event);

        // Assert
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                verify(clinicalService, times(2)).processAppointmentCompleted(any()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM session_records WHERE appointment_id = ?",
                Long.class, appointmentId)).isEqualTo(1L);
    }

    @Test
    void completedAppointmentClaimedByAnotherPsychologistsSignedNoteGoesToItsOwner() {
        // Arrange
        UUID appointmentId = UUID.randomUUID();
        UUID claimId = legacySignedClaim(appointmentId);
        Map<String, Object> event = appointmentCompleted(appointmentId);

        // Act
        rabbit.convertAndSend(EXCHANGE, "appointment.completed", event);

        // Assert
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(sessions.findByAppointmentId(new AppointmentId(appointmentId)))
                        .hasValueSatisfying(session -> assertThat(session.psychologistId().value())
                                .isEqualTo(event.get("psychologistId"))));
        assertThat(jdbc.queryForMap("SELECT appointment_id, note_status, summary FROM session_records WHERE id = ?",
                claimId)).containsEntry("appointment_id", null).containsEntry("note_status", "SIGNED")
                .containsEntry("summary", "Legacy signed note");
    }

    @Test
    void releasedClaimIsAuditedOnceWhenTheCompletionIsRedelivered() {
        // Arrange
        UUID appointmentId = UUID.randomUUID();
        UUID claimId = legacySignedClaim(appointmentId);
        Map<String, Object> event = appointmentCompleted(appointmentId);

        // Act
        rabbit.convertAndSend(EXCHANGE, "appointment.completed", event);
        rabbit.convertAndSend(EXCHANGE, "appointment.completed", event);

        // Assert
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                verify(clinicalService, times(2)).processAppointmentCompleted(any()));
        assertThat(jdbc.queryForList("SELECT source FROM session_record_link_conflicts WHERE session_record_id = ?",
                String.class, claimId)).containsExactly("APPOINTMENT_COMPLETED");
    }

    @Test
    void failingEventIsAttemptedFourTimes() {
        // Arrange
        failEveryProcessingAttempt();

        // Act
        rabbit.convertAndSend(EXCHANGE, "appointment.completed", appointmentCompleted(UUID.randomUUID()));

        // Assert
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                verify(clinicalService, times(4)).processAppointmentCompleted(any()));
    }

    @Test
    void exhaustedEventIsDeadLetteredExactlyOnceWithFailureRoutingKey() {
        // Arrange
        failEveryProcessingAttempt();
        UUID appointmentId = UUID.randomUUID();

        // Act
        rabbit.convertAndSend(EXCHANGE, "appointment.completed", appointmentCompleted(appointmentId));

        // Assert
        assertThat(deadLettersFor(appointmentId)).singleElement().satisfies(deadLetter ->
                assertThat(deadLetter.getMessageProperties().getReceivedRoutingKey()).isEqualTo("clinical.failed"));
    }

    private void failEveryProcessingAttempt() {
        doThrow(new IllegalStateException("forced consumer failure"))
                .when(clinicalService).processAppointmentCompleted(any());
    }

    private List<Message> deadLettersFor(UUID appointmentId) {
        List<Message> matches = new ArrayList<>();
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (true) {
            long timeout = matches.isEmpty()
                    ? Duration.ofNanos(deadline - System.nanoTime()).toMillis()
                    : 250;
            if (timeout <= 0) {
                return matches;
            }
            Message message = rabbit.receive(DLQ, timeout);
            if (message == null) {
                return matches;
            }
            if (new String(message.getBody(), StandardCharsets.UTF_8).contains(appointmentId.toString())) {
                matches.add(message);
            }
        }
    }

    // Before ADR-021 a manual record could take any appointment id; this rebuilds such a row, already signed.
    private UUID legacySignedClaim(UUID appointmentId) {
        UUID claimId = UUID.randomUUID();
        UUID claimant = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO session_records (id, psychologist_id, patient_id, appointment_id, session_date, modality,
                    duration_minutes, attendance_status, note_status, summary, created_at, updated_at, signed_at,
                    signed_by)
                VALUES (?, ?, ?, ?, ?, 'ONLINE', 50, 'ATTENDED', 'SIGNED', 'Legacy signed note', ?, ?, ?, ?)
                """, claimId, claimant, UUID.randomUUID(), appointmentId, now, now, now, now, claimant);
        return claimId;
    }

    private Map<String, Object> appointmentCompleted(UUID appointmentId) {
        return Map.of(
                "eventId", UUID.randomUUID(),
                "eventType", "AppointmentCompleted",
                "occurredAt", Instant.now(),
                "appointmentId", appointmentId,
                "patientId", UUID.randomUUID(),
                "psychologistId", UUID.randomUUID(),
                "appointmentDate", Instant.now().minusSeconds(3600),
                "appointmentType", "ONLINE");
    }
}
