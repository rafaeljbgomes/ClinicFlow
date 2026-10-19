package com.clinicflow.patient.adapters.outbound.messaging;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventDispatcherTest {
    private final SpringDataOutboxEventRepository repository = mock(SpringDataOutboxEventRepository.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final OutboxMetrics metrics = new OutboxMetrics(repository, registry);
    private final OutboxEventDispatcher dispatcher = new OutboxEventDispatcher(
            repository, rabbitTemplate, "therapy.events.exchange", properties(), metrics);

    @Test
    void sendsStoredPayloadAsRawJsonMessage() {
        // Arrange
        JpaOutboxEvent event = dueEvent();
        brokerAnswers(correlationData -> correlationData.getFuture().complete(new CorrelationData.Confirm(true, null)));

        // Act
        dispatcher.dispatchNext();

        // Assert
        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("therapy.events.exchange"), eq("patient.created"),
                message.capture(), any(CorrelationData.class));
        assertThat(new String(message.getValue().getBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"eventType\":\"PatientCreated\"}");
        assertThat(message.getValue().getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getValue().getMessageProperties().getMessageId()).isEqualTo(event.eventId().toString());
        assertThat(message.getValue().getMessageProperties().getType()).isEqualTo("PatientCreated");
        assertThat(message.getValue().getMessageProperties().getHeader("X-Correlation-Id").toString())
                .isEqualTo("correlation-123");
    }

    @Test
    void marksAcknowledgedEventPublished() {
        // Arrange
        JpaOutboxEvent event = dueEvent();
        brokerAnswers(correlationData -> correlationData.getFuture().complete(new CorrelationData.Confirm(true, null)));

        // Act
        boolean dispatched = dispatcher.dispatchNext();

        // Assert
        assertThat(dispatched).isTrue();
        assertThat(event.status()).isEqualTo(OutboxEventStatus.PUBLISHED);
    }

    @Test
    void countsAcknowledgedEventAsSuccessfulPublication() {
        // Arrange
        dueEvent();
        brokerAnswers(correlationData -> correlationData.getFuture().complete(new CorrelationData.Confirm(true, null)));

        // Act
        dispatcher.dispatchNext();

        // Assert
        assertThat(registry.get("clinicflow.domain.events.published")
                .tag("status", "success").counter().count()).isEqualTo(1);
    }

    @Test
    void keepsReturnedEventRetryable() {
        // Arrange
        JpaOutboxEvent event = dueEvent();
        brokerAnswers(this::returnAsUnroutable);

        // Act
        boolean dispatched = dispatcher.dispatchNext();

        // Assert
        assertThat(dispatched).isTrue();
        assertThat(event.status()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.retryCount()).isOne();
        assertThat(event.lastError()).contains("unroutable");
        assertThat(event.nextAttemptAt()).isAfter(Instant.now().minusSeconds(1));
    }

    @Test
    void countsReturnedEventAsFailedPublication() {
        // Arrange
        dueEvent();
        brokerAnswers(this::returnAsUnroutable);

        // Act
        dispatcher.dispatchNext();

        // Assert
        assertThat(registry.get("clinicflow.domain.events.published")
                .tag("status", "failure").counter().count()).isEqualTo(1);
    }

    @Test
    void keepsNegativelyAcknowledgedEventRetryable() {
        // Arrange
        JpaOutboxEvent event = dueEvent();
        brokerAnswers(correlationData -> correlationData.getFuture()
                .complete(new CorrelationData.Confirm(false, "broker rejected")));

        // Act
        boolean dispatched = dispatcher.dispatchNext();

        // Assert
        assertThat(dispatched).isTrue();
        assertThat(event.status()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.lastError()).contains("broker rejected");
    }

    @Test
    void reportsNoWorkWhenNoEventIsDue() {
        // Arrange
        when(repository.lockNextDue(any())).thenReturn(Optional.empty());

        // Act
        boolean dispatched = dispatcher.dispatchNext();

        // Assert
        assertThat(dispatched).isFalse();
    }

    @Test
    void removesOnlyRowsSelectedByPublishedRetentionQuery() {
        // Arrange
        when(repository.deletePublishedBefore(any())).thenReturn(2);

        // Act
        int removed = dispatcher.cleanupPublished();

        // Assert
        assertThat(removed).isEqualTo(2);
        verify(repository).deletePublishedBefore(any());
    }

    private JpaOutboxEvent dueEvent() {
        JpaOutboxEvent event = JpaOutboxEvent.pending(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "PatientCreated",
                "patient.created",
                "{\"eventType\":\"PatientCreated\"}",
                "correlation-123",
                Instant.now().minusSeconds(1)
        );
        when(repository.lockNextDue(any())).thenReturn(Optional.of(event));
        return event;
    }

    private void brokerAnswers(Consumer<CorrelationData> confirmation) {
        doAnswer(invocation -> {
            confirmation.accept(invocation.getArgument(3));
            return null;
        }).when(rabbitTemplate).send(any(), any(), any(Message.class), any(CorrelationData.class));
    }

    private void returnAsUnroutable(CorrelationData correlationData) {
        correlationData.setReturned(new ReturnedMessage(
                new Message(new byte[0]), 312, "NO_ROUTE", "therapy.events.exchange", "patient.created"));
        correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
    }

    private OutboxProperties properties() {
        return new OutboxProperties(true, Duration.ofSeconds(1), 20, Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofHours(1), Duration.ofDays(7));
    }
}
