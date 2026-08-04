package com.clinicflow.patient.adapters.outbound.messaging;

import com.clinicflow.patient.domain.EmailAddress;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PsychologistId;
import com.clinicflow.patient.domain.events.PatientCreatedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitPatientEventPublisherTest {
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RabbitPatientEventPublisher publisher = new RabbitPatientEventPublisher(
            rabbitTemplate,
            "therapy.events.exchange",
            registry,
            Mappers.getMapper(PatientEventMapper.class)
    );

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    void sendsMappedEventWithMessageAndCorrelationIds() {
        PatientCreatedEvent event = event();
        MDC.put("correlation.id", "correlation-123");

        publisher.publish(event);

        ArgumentCaptor<MessagePostProcessor> processor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(
                eq("therapy.events.exchange"),
                eq("patient.created"),
                any(PatientCreatedPayload.class),
                processor.capture()
        );
        Message message = processor.getValue().postProcessMessage(
                new Message(new byte[0], new MessageProperties()));
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.eventId().toString());
        assertThat(message.getMessageProperties().getHeader("X-Correlation-Id").toString())
                .isEqualTo("correlation-123");
        assertThat(counter("success")).isEqualTo(1);
    }

    @Test
    void recordsFailureAndPropagatesRabbitError() {
        doThrow(new IllegalStateException("broker unavailable")).when(rabbitTemplate)
                .convertAndSend(eq("therapy.events.exchange"), eq("patient.created"),
                        any(PatientCreatedPayload.class), any(MessagePostProcessor.class));

        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("broker unavailable");
        assertThat(counter("failure")).isEqualTo(1);
    }

    private double counter(String status) {
        return registry.get("clinicflow.domain.events.published")
                .tag("service", "patient-service")
                .tag("event_type", "PatientCreated")
                .tag("status", status)
                .counter()
                .count();
    }

    private PatientCreatedEvent event() {
        return new PatientCreatedEvent(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "PatientCreated",
                Instant.parse("2026-06-01T10:00:00Z"),
                new PatientId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")),
                new PsychologistId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")),
                new EmailAddress("patient@example.com")
        );
    }
}
