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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
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
    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
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
    void completedAppointmentCrossesRabbitAndCreatesOneClinicalSession() {
        UUID appointmentId = UUID.randomUUID();
        Map<String, Object> event = Map.of(
                "eventId", UUID.randomUUID(),
                "eventType", "AppointmentCompleted",
                "occurredAt", Instant.now(),
                "appointmentId", appointmentId,
                "patientId", UUID.randomUUID(),
                "psychologistId", UUID.randomUUID(),
                "appointmentDate", Instant.now().minusSeconds(3600),
                "appointmentType", "ONLINE");

        rabbit.convertAndSend("therapy.events.exchange", "appointment.completed", event);
        rabbit.convertAndSend("therapy.events.exchange", "appointment.completed", event);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var session = sessions.findByAppointmentId(new AppointmentId(appointmentId));
            assertThat(session).isPresent();
            assertThat(session.orElseThrow().modality().name()).isEqualTo("ONLINE");
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM session_records WHERE appointment_id = ?",
                Long.class, appointmentId)).isEqualTo(1L);
    }

    @Test
    void retriesFourTimesThenRejectsExactlyOnceToClinicalDlq() {
        doThrow(new IllegalStateException("forced consumer failure"))
                .when(clinicalService).processAppointmentCompleted(any());
        Map<String, Object> event = Map.of(
                "eventId", UUID.randomUUID(),
                "eventType", "AppointmentCompleted",
                "occurredAt", Instant.now(),
                "appointmentId", UUID.randomUUID(),
                "patientId", UUID.randomUUID(),
                "psychologistId", UUID.randomUUID(),
                "appointmentDate", Instant.now().minusSeconds(3600),
                "appointmentType", "ONLINE");

        rabbit.convertAndSend("therapy.events.exchange", "appointment.completed", event);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                verify(clinicalService, times(4)).processAppointmentCompleted(any()));
        Message deadLetter = rabbit.receive("clinical.appointment-events.dlq", 5000);
        assertThat(deadLetter).isNotNull();
        assertThat(deadLetter.getMessageProperties().getReceivedRoutingKey()).isEqualTo("clinical.failed");
        assertThat(rabbit.receive("clinical.appointment-events.dlq", 250)).isNull();
    }
}
