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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
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
class AppointmentOutboxRelayIT {
    private static final String EXCHANGE = "therapy.events.exchange";
    private static final String QUEUE = "test.appointment.outbox";

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

    @Autowired private AppointmentEventPublisher publisher;
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
    void publishesEveryExistingJsonContractWithStableMetadata() {
        AppointmentScheduledEvent scheduled = scheduled();
        MDC.put("correlation.id", "correlation-123");
        publisher.publish(scheduled);
        publisher.publish(rescheduled());
        publisher.publish(cancelled());
        publisher.publish(completed());
        MDC.clear();

        assertThat(repository.findAll()).hasSize(4)
                .allSatisfy(stored -> assertThat(stored.status()).isEqualTo(OutboxEventStatus.PENDING));

        for (int i = 0; i < 4; i++) {
            assertThat(dispatcher.dispatchNext()).isTrue();
        }

        List<Message> messages = List.of(
                receive(), receive(), receive(), receive());
        assertThat(messages).extracting(message -> message.getMessageProperties().getReceivedRoutingKey())
                .containsExactly(
                        "appointment.scheduled",
                        "appointment.rescheduled",
                        "appointment.cancelled",
                        "appointment.completed");
        assertThat(messages).allSatisfy(message -> {
            assertThat(message.getMessageProperties().getMessageId()).isNotBlank();
            assertThat(message.getMessageProperties().getHeader("X-Correlation-Id").toString())
                    .isEqualTo("correlation-123");
            assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).startsWith("{").endsWith("}");
        });
        assertThat(messages.get(0).getMessageProperties().getMessageId())
                .isEqualTo(scheduled.eventId().value().toString());
        assertThat(new String(messages.get(0).getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"AppointmentScheduled\"");
        assertThat(new String(messages.get(1).getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"AppointmentRescheduled\"");
        assertThat(new String(messages.get(2).getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"AppointmentCancelled\"");
        assertThat(new String(messages.get(3).getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"AppointmentCompleted\"")
                .contains("\"appointmentType\":\"ONLINE\"");
        assertThat(repository.findAll()).allSatisfy(stored ->
                assertThat(stored.status()).isEqualTo(OutboxEventStatus.PUBLISHED));
    }

    @Test
    void commitsBusinessHandoffWhileBrokerIsDownThenPublishesAfterRecovery() throws Exception {
        publisher.publish(scheduled());
        assertThat(RABBITMQ.execInContainer("rabbitmqctl", "stop_app").getExitCode()).isZero();
        resetRabbitConnection();

        assertThat(dispatcher.dispatchNext()).isTrue();
        assertThat(repository.findAll()).singleElement().satisfies(stored -> {
            assertThat(stored.status()).isEqualTo(OutboxEventStatus.FAILED);
            assertThat(stored.retryCount()).isOne();
        });

        assertThat(RABBITMQ.execInContainer("rabbitmqctl", "start_app").getExitCode()).isZero();
        resetRabbitConnection();
        declareQueue();

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            dispatcher.dispatchNext();
            assertThat(repository.findAll()).singleElement()
                    .extracting(JpaOutboxEvent::status)
                    .isEqualTo(OutboxEventStatus.PUBLISHED);
        });
        assertThat(receive()).isNotNull();
    }

    @Test
    void skipLockedClaimPublishesOneRowOnceAcrossConcurrentDispatchers() throws Exception {
        publisher.publish(scheduled());

        List<Boolean> outcomes;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(dispatcher::dispatchNext);
            var second = executor.submit(dispatcher::dispatchNext);
            outcomes = List.of(first.get(), second.get());
        }

        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        assertThat(receive()).isNotNull();
        assertThat(rabbitTemplate.receive(QUEUE, 250)).isNull();
    }

    @Test
    void replayAfterLostStatusUpdateKeepsTheSameIdempotencyKey() {
        AppointmentScheduledEvent event = scheduled();
        publisher.publish(event);
        assertThat(dispatcher.dispatchNext()).isTrue();

        jdbc.update("""
                UPDATE outbox_events
                SET status = 'FAILED', published_at = NULL, next_attempt_at = now()
                WHERE event_id = ?
                """, event.eventId().value());
        assertThat(dispatcher.dispatchNext()).isTrue();

        Message first = receive();
        Message second = receive();
        assertThat(first.getMessageProperties().getMessageId())
                .isEqualTo(second.getMessageProperties().getMessageId())
                .isEqualTo(event.eventId().value().toString());
    }

    @Test
    void migrationCreatesDispatchAndRetentionIndexes() {
        assertThat(jdbc.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE tablename = 'outbox_events'
                """, String.class))
                .contains("idx_outbox_dispatch", "idx_outbox_published_at");
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

    private AppointmentScheduledEvent scheduled() {
        return new AppointmentScheduledEvent(eventId(), "AppointmentScheduled", occurredAt(), appointmentId(),
                patientId(), psychologistId(), new AppointmentDate(Instant.parse("2099-07-01T10:00:00Z")));
    }

    private AppointmentRescheduledEvent rescheduled() {
        return new AppointmentRescheduledEvent(eventId(), "AppointmentRescheduled", occurredAt(), appointmentId(),
                patientId(), psychologistId(),
                new AppointmentDate(Instant.parse("2099-07-01T10:00:00Z")),
                new AppointmentDate(Instant.parse("2099-07-02T10:00:00Z")));
    }

    private AppointmentCancelledEvent cancelled() {
        return new AppointmentCancelledEvent(eventId(), "AppointmentCancelled", occurredAt(), appointmentId(),
                patientId(), psychologistId(),
                new CancellationActor("psychologist"), new CancellationReason("Patient requested cancellation"));
    }

    private AppointmentCompletedEvent completed() {
        return new AppointmentCompletedEvent(eventId(), "AppointmentCompleted", occurredAt(), appointmentId(),
                patientId(), psychologistId(), new AppointmentDate(Instant.parse("2099-07-01T10:00:00Z")),
                AppointmentType.ONLINE);
    }

    private DomainEventId eventId() {
        return new DomainEventId(UUID.randomUUID());
    }

    private AppointmentId appointmentId() {
        return new AppointmentId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    }

    private PatientId patientId() {
        return new PatientId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    }

    private PsychologistId psychologistId() {
        return new PsychologistId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));
    }

    private Instant occurredAt() {
        return Instant.parse("2026-06-01T10:00:00Z");
    }
}
