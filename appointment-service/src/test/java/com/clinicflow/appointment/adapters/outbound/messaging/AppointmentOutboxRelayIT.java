package com.clinicflow.appointment.adapters.outbound.messaging;

import com.clinicflow.appointment.application.ports.AppointmentEventPublisher;
import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.AppointmentType;
import com.clinicflow.appointment.domain.CancellationActor;
import com.clinicflow.appointment.domain.CancellationReason;
import com.clinicflow.appointment.domain.DomainEventId;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;
import com.clinicflow.appointment.domain.events.AppointmentCancelledEvent;
import com.clinicflow.appointment.domain.events.AppointmentCompletedEvent;
import com.clinicflow.appointment.domain.events.AppointmentRescheduledEvent;
import com.clinicflow.appointment.domain.events.AppointmentScheduledEvent;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "clinicflow.demo.enabled=false",
        "clinicflow.outbox.enabled=false",
        "clinicflow.outbox.retry-initial=1ms",
        "clinicflow.outbox.retry-max=5ms",
        "clinicflow.outbox.confirm-timeout=2s"
})
@Testcontainers
class AppointmentOutboxRelayIT {
    private static final String EXCHANGE = "therapy.events.exchange";
    private static final String QUEUE = "test.appointment.outbox";

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

    @Autowired private AppointmentEventPublisher publisher;
    @Autowired private OutboxEventDispatcher dispatcher;
    @Autowired private SpringDataOutboxEventRepository repository;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private ConnectionFactory connectionFactory;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private JwtDecoder jwtDecoder;

    static Stream<Arguments> appointmentEvents() {
        return Stream.of(
                Arguments.of("AppointmentScheduled", "appointment.scheduled",
                        (Supplier<Object>) AppointmentOutboxRelayIT::scheduled),
                Arguments.of("AppointmentRescheduled", "appointment.rescheduled",
                        (Supplier<Object>) AppointmentOutboxRelayIT::rescheduled),
                Arguments.of("AppointmentCancelled", "appointment.cancelled",
                        (Supplier<Object>) AppointmentOutboxRelayIT::cancelled),
                Arguments.of("AppointmentCompleted", "appointment.completed",
                        (Supplier<Object>) AppointmentOutboxRelayIT::completed));
    }

    @BeforeEach
    void resetState() throws Exception {
        RABBITMQ.execInContainer("rabbitmqctl", "start_app");
        resetRabbitConnection();
        declareQueue();
        new RabbitAdmin(connectionFactory).purgeQueue(QUEUE, false);
        jdbc.execute("TRUNCATE TABLE outbox_events");
        MDC.clear();
    }

    @Test
    void publishingStoresPendingRowBeforeAnyRelay() {
        // Arrange
        AppointmentScheduledEvent event = scheduled();

        // Act
        publisher.publish(event);

        // Assert
        assertThat(repository.findAll()).singleElement().satisfies(stored -> {
            assertThat(stored.eventId()).isEqualTo(event.eventId().value());
            assertThat(stored.status()).isEqualTo(OutboxEventStatus.PENDING);
        });
    }

    @ParameterizedTest(name = "{0} is relayed on {1}")
    @MethodSource("appointmentEvents")
    void relaysStoredEventWithExistingJsonContractAndStableMetadata(String eventType, String routingKey,
                                                                    Supplier<Object> event) {
        // Arrange
        MDC.put("correlation.id", "correlation-123");
        publish(event.get());
        MDC.clear();

        // Act
        dispatcher.dispatchNext();

        // Assert
        Message message = receive();
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo(routingKey);
        assertThat(message.getMessageProperties().getMessageId())
                .isEqualTo(repository.findAll().getFirst().eventId().toString());
        assertThat(message.getMessageProperties().getHeader("X-Correlation-Id").toString())
                .isEqualTo("correlation-123");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .startsWith("{").endsWith("}")
                .contains("\"eventType\":\"" + eventType + "\"");
    }

    @Test
    void relaysCompletedEventWithItsAppointmentType() {
        // Arrange
        publisher.publish(completed());

        // Act
        dispatcher.dispatchNext();

        // Assert
        assertThat(new String(receive().getBody(), StandardCharsets.UTF_8))
                .contains("\"appointmentType\":\"ONLINE\"");
    }

    @Test
    void marksRelayedRowPublished() {
        // Arrange
        publisher.publish(scheduled());

        // Act
        boolean dispatched = dispatcher.dispatchNext();

        // Assert
        assertThat(dispatched).isTrue();
        assertThat(repository.findAll()).singleElement()
                .extracting(JpaOutboxEvent::status)
                .isEqualTo(OutboxEventStatus.PUBLISHED);
    }

    @Test
    void keepsCommittedRowRetryableWhileBrokerIsDown() throws Exception {
        // Arrange
        publisher.publish(scheduled());
        stopBroker();

        // Act
        boolean dispatched = dispatcher.dispatchNext();

        // Assert
        assertThat(dispatched).isTrue();
        assertThat(repository.findAll()).singleElement().satisfies(stored -> {
            assertThat(stored.status()).isEqualTo(OutboxEventStatus.FAILED);
            assertThat(stored.retryCount()).isOne();
        });
    }

    @Test
    void publishesRetainedRowAfterBrokerRecovery() throws Exception {
        // Arrange
        AppointmentScheduledEvent event = scheduled();
        publisher.publish(event);
        stopBroker();
        dispatcher.dispatchNext();
        startBroker();

        // Act
        dispatchUntilPublished();

        // Assert
        Message message = receive();
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.eventId().value().toString());
    }

    @Test
    void onlyOneConcurrentDispatcherClaimsTheRow() throws Exception {
        // Arrange
        publisher.publish(scheduled());

        // Act
        List<Boolean> outcomes = dispatchConcurrently();

        // Assert
        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
    }

    @Test
    void concurrentDispatchersPublishTheRowOnce() throws Exception {
        // Arrange
        publisher.publish(scheduled());

        // Act
        dispatchConcurrently();

        // Assert
        assertThat(receive()).isNotNull();
        assertThat(rabbitTemplate.receive(QUEUE, 250)).isNull();
    }

    @Test
    void replayAfterLostStatusUpdateKeepsTheSameIdempotencyKey() {
        // Arrange
        AppointmentScheduledEvent event = scheduled();
        publisher.publish(event);
        arrangeStep("first relay", dispatcher.dispatchNext());
        Message first = receive();
        jdbc.update("""
                UPDATE outbox_events
                SET status = 'FAILED', published_at = NULL, next_attempt_at = now()
                WHERE event_id = ?
                """, event.eventId().value());

        // Act
        dispatcher.dispatchNext();

        // Assert
        Message second = receive();
        assertThat(second).isNotNull();
        assertThat(second.getMessageProperties().getMessageId())
                .isEqualTo(first.getMessageProperties().getMessageId())
                .isEqualTo(event.eventId().value().toString());
    }

    @Test
    void migrationCreatesDispatchAndRetentionIndexes() {
        // Act
        List<String> indexes = jdbc.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE tablename = 'outbox_events'
                """, String.class);

        // Assert
        assertThat(indexes).contains("idx_outbox_dispatch", "idx_outbox_published_at");
    }

    private void publish(Object event) {
        switch (event) {
            case AppointmentScheduledEvent scheduled -> publisher.publish(scheduled);
            case AppointmentRescheduledEvent rescheduled -> publisher.publish(rescheduled);
            case AppointmentCancelledEvent cancelled -> publisher.publish(cancelled);
            case AppointmentCompletedEvent completed -> publisher.publish(completed);
            default -> throw new IllegalArgumentException("Unsupported event " + event);
        }
    }

    private List<Boolean> dispatchConcurrently() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(dispatcher::dispatchNext);
            var second = executor.submit(dispatcher::dispatchNext);
            return List.of(first.get(), second.get());
        }
    }

    private void dispatchUntilPublished() {
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> {
            dispatcher.dispatchNext();
            return repository.findAll().getFirst().status() == OutboxEventStatus.PUBLISHED;
        });
    }

    private void stopBroker() throws Exception {
        arrangeStep("stop broker", RABBITMQ.execInContainer("rabbitmqctl", "stop_app").getExitCode() == 0);
        resetRabbitConnection();
    }

    private void startBroker() throws Exception {
        arrangeStep("start broker", RABBITMQ.execInContainer("rabbitmqctl", "start_app").getExitCode() == 0);
        resetRabbitConnection();
        declareQueue();
    }

    private static void arrangeStep(String step, boolean succeeded) {
        if (!succeeded) {
            throw new IllegalStateException("Arrange step \"" + step + "\" failed");
        }
    }

    private Message receive() {
        return rabbitTemplate.receive(QUEUE, 5000);
    }

    private void declareQueue() {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        Queue queue = new Queue(QUEUE, true, false, false);
        admin.declareExchange(new TopicExchange(EXCHANGE, true, false));
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue)
                .to(new TopicExchange(EXCHANGE, true, false))
                .with("appointment.#"));
    }

    private void resetRabbitConnection() {
        if (connectionFactory instanceof CachingConnectionFactory cachingConnectionFactory) {
            cachingConnectionFactory.resetConnection();
        }
    }

    private static AppointmentScheduledEvent scheduled() {
        return new AppointmentScheduledEvent(eventId(), "AppointmentScheduled", occurredAt(), appointmentId(),
                patientId(), psychologistId(), new AppointmentDate(Instant.parse("2099-07-01T10:00:00Z")));
    }

    private static AppointmentRescheduledEvent rescheduled() {
        return new AppointmentRescheduledEvent(eventId(), "AppointmentRescheduled", occurredAt(), appointmentId(),
                patientId(), psychologistId(),
                new AppointmentDate(Instant.parse("2099-07-01T10:00:00Z")),
                new AppointmentDate(Instant.parse("2099-07-02T10:00:00Z")));
    }

    private static AppointmentCancelledEvent cancelled() {
        return new AppointmentCancelledEvent(eventId(), "AppointmentCancelled", occurredAt(), appointmentId(),
                patientId(), psychologistId(),
                new CancellationActor("psychologist"), new CancellationReason("Patient requested cancellation"));
    }

    private static AppointmentCompletedEvent completed() {
        return new AppointmentCompletedEvent(eventId(), "AppointmentCompleted", occurredAt(), appointmentId(),
                patientId(), psychologistId(), new AppointmentDate(Instant.parse("2099-07-01T10:00:00Z")),
                AppointmentType.ONLINE);
    }

    private static DomainEventId eventId() {
        return new DomainEventId(UUID.randomUUID());
    }

    private static AppointmentId appointmentId() {
        return new AppointmentId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    }

    private static PatientId patientId() {
        return new PatientId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    }

    private static PsychologistId psychologistId() {
        return new PsychologistId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));
    }

    private static Instant occurredAt() {
        return Instant.parse("2026-06-01T10:00:00Z");
    }
}
