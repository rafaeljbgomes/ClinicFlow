package com.clinicflow.appointment.adapters.outbound.messaging;

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
    void publishesRawJsonAndMarksAcknowledgedEventPublished() {
        JpaOutboxEvent event = event();
        when(repository.lockNextDue(any())).thenReturn(Optional.of(event));
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(eq("therapy.events.exchange"), eq("appointment.scheduled"),
                any(Message.class), any(CorrelationData.class));

        assertThat(dispatcher.dispatchNext()).isTrue();

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("therapy.events.exchange"), eq("appointment.scheduled"),
                message.capture(), any(CorrelationData.class));
        assertThat(new String(message.getValue().getBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"eventType\":\"AppointmentScheduled\"}");
        assertThat(message.getValue().getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getValue().getMessageProperties().getMessageId()).isEqualTo(event.eventId().toString());
        assertThat(message.getValue().getMessageProperties().getType()).isEqualTo("AppointmentScheduled");
        assertThat(message.getValue().getMessageProperties().getHeader("X-Correlation-Id").toString())
                .isEqualTo("correlation-123");
        assertThat(event.status()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(registry.get("clinicflow.domain.events.published")
                .tag("status", "success").counter().count()).isEqualTo(1);
    }

    @Test
    void keepsReturnedEventRetryable() {
        JpaOutboxEvent event = event();
        when(repository.lockNextDue(any())).thenReturn(Optional.of(event));
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            Message returned = new Message(new byte[0]);
            correlationData.setReturned(new ReturnedMessage(
                    returned, 312, "NO_ROUTE", "therapy.events.exchange", "appointment.scheduled"));
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(any(), any(), any(Message.class), any(CorrelationData.class));

        assertThat(dispatcher.dispatchNext()).isTrue();

        assertThat(event.status()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.retryCount()).isOne();
        assertThat(event.lastError()).contains("unroutable");
        assertThat(event.nextAttemptAt()).isAfter(Instant.now().minusSeconds(1));
        assertThat(registry.get("clinicflow.domain.events.published")
                .tag("status", "failure").counter().count()).isEqualTo(1);
    }

    @Test
    void retriesNegativeAcknowledgementAndReportsEmptyQueue() {
        JpaOutboxEvent event = event();
        when(repository.lockNextDue(any())).thenReturn(Optional.of(event), Optional.empty());
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(false, "broker rejected"));
            return null;
        }).when(rabbitTemplate).send(any(), any(), any(Message.class), any(CorrelationData.class));

        assertThat(dispatcher.dispatchNext()).isTrue();
        assertThat(event.status()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.lastError()).contains("broker rejected");
        assertThat(dispatcher.dispatchNext()).isFalse();
    }

    @Test
    void removesOnlyRowsSelectedByPublishedRetentionQuery() {
        when(repository.deletePublishedBefore(any())).thenReturn(2);

        assertThat(dispatcher.cleanupPublished()).isEqualTo(2);

        verify(repository).deletePublishedBefore(any());
    }

    private JpaOutboxEvent event() {
        return JpaOutboxEvent.pending(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "AppointmentScheduled",
                "appointment.scheduled",
                "{\"eventType\":\"AppointmentScheduled\"}",
                "correlation-123",
                Instant.now().minusSeconds(1)
        );
    }

    private OutboxProperties properties() {
        return new OutboxProperties(true, Duration.ofSeconds(1), 20, Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofHours(1), Duration.ofDays(7));
    }
}
