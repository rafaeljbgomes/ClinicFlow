package com.clinicflow.appointment.adapters.outbound.messaging;

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

class RabbitAppointmentEventPublisherTest {
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RabbitAppointmentEventPublisher publisher = new RabbitAppointmentEventPublisher(
            rabbitTemplate,
            "therapy.events.exchange",
            registry,
            Mappers.getMapper(AppointmentEventMapper.class)
    );

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    void publishesEveryAppointmentEventWithExpectedRoutingKey() {
        publisher.publish(scheduled());
        publisher.publish(rescheduled());
        publisher.publish(cancelled());
        publisher.publish(completed());

        verify(rabbitTemplate).convertAndSend(eq("therapy.events.exchange"), eq("appointment.scheduled"),
                any(AppointmentScheduledPayload.class), any(MessagePostProcessor.class));
        verify(rabbitTemplate).convertAndSend(eq("therapy.events.exchange"), eq("appointment.rescheduled"),
                any(AppointmentRescheduledPayload.class), any(MessagePostProcessor.class));
        verify(rabbitTemplate).convertAndSend(eq("therapy.events.exchange"), eq("appointment.cancelled"),
                any(AppointmentCancelledPayload.class), any(MessagePostProcessor.class));
        verify(rabbitTemplate).convertAndSend(eq("therapy.events.exchange"), eq("appointment.completed"),
                any(AppointmentCompletedPayload.class), any(MessagePostProcessor.class));
        assertThat(counter("AppointmentScheduled", "success")).isEqualTo(1);
        assertThat(counter("AppointmentRescheduled", "success")).isEqualTo(1);
        assertThat(counter("AppointmentCancelled", "success")).isEqualTo(1);
        assertThat(counter("AppointmentCompleted", "success")).isEqualTo(1);
    }

    @Test
    void addsMessageAndCorrelationIds() {
        AppointmentScheduledEvent event = scheduled();
        MDC.put("correlation.id", "correlation-123");
        ArgumentCaptor<MessagePostProcessor> processor = ArgumentCaptor.forClass(MessagePostProcessor.class);

        publisher.publish(event);

        verify(rabbitTemplate).convertAndSend(eq("therapy.events.exchange"), eq("appointment.scheduled"),
                any(AppointmentScheduledPayload.class), processor.capture());
        Message message = processor.getValue().postProcessMessage(
                new Message(new byte[0], new MessageProperties()));
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.eventId().value().toString());
        assertThat(message.getMessageProperties().getHeader("X-Correlation-Id").toString())
                .isEqualTo("correlation-123");
    }

    @Test
    void recordsFailureAndPropagatesRabbitError() {
        doThrow(new IllegalStateException("broker unavailable")).when(rabbitTemplate)
                .convertAndSend(eq("therapy.events.exchange"), eq("appointment.scheduled"),
                        any(AppointmentScheduledPayload.class), any(MessagePostProcessor.class));

        assertThatThrownBy(() -> publisher.publish(scheduled()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(counter("AppointmentScheduled", "failure")).isEqualTo(1);
    }

    private double counter(String eventType, String status) {
        return registry.get("clinicflow.domain.events.published")
                .tag("service", "appointment-service")
                .tag("event_type", eventType)
                .tag("status", status)
                .counter()
                .count();
    }

    private AppointmentScheduledEvent scheduled() {
        return new AppointmentScheduledEvent(eventId(), "AppointmentScheduled", occurredAt(), appointmentId(),
                patientId(), psychologistId(), new AppointmentDate(Instant.parse("2026-07-01T10:00:00Z")));
    }

    private AppointmentRescheduledEvent rescheduled() {
        return new AppointmentRescheduledEvent(eventId(), "AppointmentRescheduled", occurredAt(), appointmentId(),
                patientId(), psychologistId(),
                new AppointmentDate(Instant.parse("2026-07-01T10:00:00Z")),
                new AppointmentDate(Instant.parse("2026-07-02T10:00:00Z")));
    }

    private AppointmentCancelledEvent cancelled() {
        return new AppointmentCancelledEvent(eventId(), "AppointmentCancelled", occurredAt(), appointmentId(),
                patientId(), psychologistId(),
                new CancellationActor("psychologist"), new CancellationReason("Patient requested cancellation"));
    }

    private AppointmentCompletedEvent completed() {
        return new AppointmentCompletedEvent(eventId(), "AppointmentCompleted", occurredAt(), appointmentId(),
                patientId(), psychologistId(), new AppointmentDate(Instant.parse("2026-07-01T10:00:00Z")),
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
