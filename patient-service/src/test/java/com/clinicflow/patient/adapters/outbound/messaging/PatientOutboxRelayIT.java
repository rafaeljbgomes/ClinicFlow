package com.clinicflow.patient.adapters.outbound.messaging;

import com.clinicflow.patient.application.ports.PatientEventPublisher;
import com.clinicflow.patient.domain.EmailAddress;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PsychologistId;
import com.clinicflow.patient.domain.events.PatientCreatedEvent;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "clinicflow.demo.enabled=false",
        "clinicflow.outbox.enabled=false",
        "clinicflow.outbox.retry-initial=1ms",
        "clinicflow.outbox.retry-max=5ms",
        "clinicflow.outbox.confirm-timeout=2s"
})
@Testcontainers
class PatientOutboxRelayIT {
    private static final String EXCHANGE = "therapy.events.exchange";
    private static final String QUEUE = "test.patient.outbox";

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

    @Autowired private PatientEventPublisher publisher;
    @Autowired private OutboxEventDispatcher dispatcher;
    @Autowired private SpringDataOutboxEventRepository repository;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private ConnectionFactory connectionFactory;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private JwtDecoder jwtDecoder;

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
        PatientCreatedEvent event = event();

        // Act
        publisher.publish(event);

        // Assert
        assertThat(repository.findAll()).singleElement().satisfies(stored -> {
            assertThat(stored.eventId()).isEqualTo(event.eventId());
            assertThat(stored.status()).isEqualTo(OutboxEventStatus.PENDING);
        });
    }

    @Test
    void relaysStoredEventWithExistingJsonContractAndStableMetadata() {
        // Arrange
        PatientCreatedEvent event = event();
        MDC.put("correlation.id", "correlation-123");
        publisher.publish(event);
        MDC.clear();

        // Act
        dispatcher.dispatchNext();

        // Assert
        Message message = receive();
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("patient.created");
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.eventId().toString());
        assertThat(message.getMessageProperties().getHeader("X-Correlation-Id").toString())
                .isEqualTo("correlation-123");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"PatientCreated\"")
                .contains("\"patientEmail\":\"patient@example.com\"");
    }

    @Test
    void marksRelayedRowPublished() {
        // Arrange
        publisher.publish(event());

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
        publisher.publish(event());
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
        PatientCreatedEvent event = event();
        publisher.publish(event);
        stopBroker();
        dispatcher.dispatchNext();
        startBroker();

        // Act
        dispatchUntilPublished();

        // Assert
        Message message = receive();
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.eventId().toString());
    }

    @Test
    void onlyOneConcurrentDispatcherClaimsTheRow() throws Exception {
        // Arrange
        publisher.publish(event());

        // Act
        List<Boolean> outcomes = dispatchConcurrently();

        // Assert
        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
    }

    @Test
    void concurrentDispatchersPublishTheRowOnce() throws Exception {
        // Arrange
        publisher.publish(event());

        // Act
        dispatchConcurrently();

        // Assert
        assertThat(receive()).isNotNull();
        assertThat(rabbitTemplate.receive(QUEUE, 250)).isNull();
    }

    @Test
    void replayAfterLostStatusUpdateKeepsTheSameIdempotencyKey() {
        // Arrange
        PatientCreatedEvent event = event();
        publisher.publish(event);
        arrangeStep("first relay", dispatcher.dispatchNext());
        Message first = receive();
        jdbc.update("""
                UPDATE outbox_events
                SET status = 'FAILED', published_at = NULL, next_attempt_at = now()
                WHERE event_id = ?
                """, event.eventId());

        // Act
        dispatcher.dispatchNext();

        // Assert
        Message second = receive();
        assertThat(second).isNotNull();
        assertThat(second.getMessageProperties().getMessageId())
                .isEqualTo(first.getMessageProperties().getMessageId())
                .isEqualTo(event.eventId().toString());
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
                .with("patient.created"));
    }

    private void resetRabbitConnection() {
        if (connectionFactory instanceof CachingConnectionFactory cachingConnectionFactory) {
            cachingConnectionFactory.resetConnection();
        }
    }

    private PatientCreatedEvent event() {
        return new PatientCreatedEvent(
                UUID.randomUUID(),
                "PatientCreated",
                Instant.parse("2026-06-01T10:00:00Z"),
                new PatientId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")),
                new PsychologistId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")),
                new EmailAddress("patient@example.com")
        );
    }
}
