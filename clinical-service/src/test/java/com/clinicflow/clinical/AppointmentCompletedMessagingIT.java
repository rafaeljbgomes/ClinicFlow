package com.clinicflow.clinical;

import com.clinicflow.clinical.application.ports.SessionRecordRepository;
import com.clinicflow.clinical.domain.AppointmentId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

@SpringBootTest(properties = "clinicflow.demo.enabled=false")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AppointmentCompletedMessagingIT {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.3.4-alpine");

    @Autowired private RabbitTemplate rabbit;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SessionRecordRepository sessions;
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
}
