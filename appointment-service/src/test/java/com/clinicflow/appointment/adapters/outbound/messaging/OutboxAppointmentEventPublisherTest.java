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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.slf4j.MDC;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxAppointmentEventPublisherTest {
    private final SpringDataOutboxEventRepository repository = mock(SpringDataOutboxEventRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final OutboxAppointmentEventPublisher publisher = new OutboxAppointmentEventPublisher(
            repository, objectMapper, Mappers.getMapper(AppointmentEventMapper.class));

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void storesAllMappedEventsWithExpectedRoutesAndCorrelationId() {
        MDC.put("correlation.id", "correlation-123");

        publisher.publish(scheduled());
        publisher.publish(rescheduled());
        publisher.publish(cancelled());
        publisher.publish(completed());

        ArgumentCaptor<JpaOutboxEvent> captor = ArgumentCaptor.forClass(JpaOutboxEvent.class);
        verify(repository, org.mockito.Mockito.times(4)).save(captor.capture());
        List<JpaOutboxEvent> stored = captor.getAllValues();
        assertThat(stored).extracting(JpaOutboxEvent::routingKey).containsExactly(
                "appointment.scheduled",
                "appointment.rescheduled",
                "appointment.cancelled",
                "appointment.completed");
        assertThat(stored).extracting(JpaOutboxEvent::eventType).containsExactly(
                "AppointmentScheduled",
                "AppointmentRescheduled",
                "AppointmentCancelled",
                "AppointmentCompleted");
        assertThat(stored).allSatisfy(row -> {
            assertThat(row.eventId()).isNotNull();
            assertThat(row.correlationId()).isEqualTo("correlation-123");
            assertThat(row.status()).isEqualTo(OutboxEventStatus.PENDING);
            assertThat(row.retryCount()).isZero();
            assertThat(row.payload()).contains("\"appointmentId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\"");
        });
        assertThat(stored.get(0).payload()).contains("\"eventType\":\"AppointmentScheduled\"");
        assertThat(stored.get(3).payload()).contains("\"appointmentType\":\"ONLINE\"");
    }

    @Test
    void propagatesSerializationFailureWithoutWritingOutboxRow() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("broken") { });
        var failingPublisher = new OutboxAppointmentEventPublisher(
                repository, failingMapper, Mappers.getMapper(AppointmentEventMapper.class));

        assertThatThrownBy(() -> failingPublisher.publish(scheduled()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not serialize appointment event for the outbox")
                .hasCauseInstanceOf(JsonProcessingException.class);

        verify(repository, never()).save(any());
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
