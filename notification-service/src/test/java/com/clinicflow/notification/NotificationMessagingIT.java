package com.clinicflow.notification;

import com.clinicflow.notification.application.ports.NotificationRepository;
import com.clinicflow.notification.application.queries.PageQuery;
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
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final String EXCHANGE = "therapy.events.exchange";
    private static final String DLQ = "notification.events.dlq";

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
    @Autowired private NotificationRepository notifications;
    @MockitoSpyBean private NotificationService notificationService;
    @MockitoBean private JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE notifications");
    }

    @Test
    void consumedContractEventIsPersistedWithItsIdentifiers() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID psychologistId = UUID.randomUUID();
        Map<String, Object> event = patientCreated(eventId, psychologistId);

        // Act
        rabbit.convertAndSend(EXCHANGE, "patient.created", event);

        // Assert
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notifications.findPage(Set.of(), new PageQuery(0, 10)).items()).singleElement().satisfies(notification -> {
                    assertThat(notification.eventId().value()).isEqualTo(eventId);
                    assertThat(notification.psychologistId()).hasValueSatisfying(
                            id -> assertThat(id.value()).isEqualTo(psychologistId));
                }));
    }

    @Test
    void redeliveredContractEventIsPersistedOnlyOnce() {
        // Arrange
        Map<String, Object> event = patientCreated(UUID.randomUUID(), UUID.randomUUID());

        // Act
        rabbit.convertAndSend(EXCHANGE, "patient.created", event);
        rabbit.convertAndSend(EXCHANGE, "patient.created", event);

        // Assert
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                verify(notificationService, times(2)).processEvent(any()));
        assertThat(notifications.findPage(Set.of(), new PageQuery(0, 10)).items()).hasSize(1);
    }

    @Test
    void failingEventIsAttemptedFourTimes() {
        // Arrange
        failEveryProcessingAttempt();

        // Act
        rabbit.convertAndSend(EXCHANGE, "patient.created", patientCreated(UUID.randomUUID(), UUID.randomUUID()));

        // Assert
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                verify(notificationService, times(4)).processEvent(any()));
    }

    @Test
    void exhaustedEventIsDeadLetteredExactlyOnceWithFailureRoutingKey() {
        // Arrange
        failEveryProcessingAttempt();
        UUID eventId = UUID.randomUUID();

        // Act
        rabbit.convertAndSend(EXCHANGE, "patient.created", patientCreated(eventId, UUID.randomUUID()));

        // Assert
        assertThat(deadLettersFor(eventId)).singleElement().satisfies(deadLetter ->
                assertThat(deadLetter.getMessageProperties().getReceivedRoutingKey())
                        .isEqualTo("notification.failed"));
    }

    private void failEveryProcessingAttempt() {
        doThrow(new IllegalStateException("forced consumer failure"))
                .when(notificationService).processEvent(any());
    }

    private List<Message> deadLettersFor(UUID eventId) {
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
            if (new String(message.getBody(), StandardCharsets.UTF_8).contains(eventId.toString())) {
                matches.add(message);
            }
        }
    }

    private Map<String, Object> patientCreated(UUID eventId, UUID psychologistId) {
        return Map.of(
                "eventId", eventId,
                "eventType", "PatientCreated",
                "occurredAt", Instant.now(),
                "patientId", UUID.randomUUID(),
                "psychologistId", psychologistId,
                "patientEmail", "patient@example.com");
    }
}
