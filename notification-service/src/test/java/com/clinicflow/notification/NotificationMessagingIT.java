package com.clinicflow.notification;

import com.clinicflow.notification.application.ports.NotificationRepository;
import com.clinicflow.notification.application.NotificationService;
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
class NotificationMessagingIT {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.3.4-alpine");

    @Autowired private RabbitTemplate rabbit;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NotificationRepository notifications;
    @MockitoSpyBean private NotificationService notificationService;
    @MockitoBean private JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE notifications");
    }

    @Test
    void consumesAContractEventThroughRabbitAndPersistsItOnlyOnce() {
        UUID eventId = UUID.randomUUID();
        UUID psychologistId = UUID.randomUUID();
        Map<String, Object> event = Map.of(
                "eventId", eventId,
                "eventType", "PatientCreated",
                "occurredAt", Instant.now(),
                "patientId", UUID.randomUUID(),
                "psychologistId", psychologistId,
                "patientEmail", "patient@example.com");

        rabbit.convertAndSend("therapy.events.exchange", "patient.created", event);
        rabbit.convertAndSend("therapy.events.exchange", "patient.created", event);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(notifications.findAll()).hasSize(1);
            assertThat(notifications.findAll().getFirst().eventId().value()).isEqualTo(eventId);
            assertThat(notifications.findAll().getFirst().psychologistId()).hasValueSatisfying(
                    id -> assertThat(id.value()).isEqualTo(psychologistId));
        });
    }

    @Test
    void retriesFourTimesThenRejectsExactlyOnceToNotificationDlq() {
        doThrow(new IllegalStateException("forced consumer failure"))
                .when(notificationService).processEvent(any());
        Map<String, Object> event = Map.of(
                "eventId", UUID.randomUUID(),
                "eventType", "PatientCreated",
                "occurredAt", Instant.now(),
                "patientId", UUID.randomUUID(),
                "psychologistId", UUID.randomUUID(),
                "patientEmail", "patient@example.com");

        rabbit.convertAndSend("therapy.events.exchange", "patient.created", event);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                verify(notificationService, times(4)).processEvent(any()));
        Message deadLetter = rabbit.receive("notification.events.dlq", 5000);
        assertThat(deadLetter).isNotNull();
        assertThat(deadLetter.getMessageProperties().getReceivedRoutingKey()).isEqualTo("notification.failed");
        assertThat(rabbit.receive("notification.events.dlq", 250)).isNull();
    }
}
