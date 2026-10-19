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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;
import org.slf4j.MDC;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
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

    static Stream<Arguments> appointmentEvents() {
        return Stream.of(
                Arguments.of("AppointmentScheduled", "appointment.scheduled",
                        (Consumer<OutboxAppointmentEventPublisher>) publisher -> publisher.publish(scheduled())),
                Arguments.of("AppointmentRescheduled", "appointment.rescheduled",
                        (Consumer<OutboxAppointmentEventPublisher>) publisher -> publisher.publish(rescheduled())),
                Arguments.of("AppointmentCancelled", "appointment.cancelled",
                        (Consumer<OutboxAppointmentEventPublisher>) publisher -> publisher.publish(cancelled())),
                Arguments.of("AppointmentCompleted", "appointment.completed",
                        (Consumer<OutboxAppointmentEventPublisher>) publisher -> publisher.publish(completed())));
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @ParameterizedTest(name = "{0} routes to {1}")
    @MethodSource("appointmentEvents")
    void storesEventWithItsTypeAndRoutingKey(String eventType, String routingKey,
                                             Consumer<OutboxAppointmentEventPublisher> publish) {
        // Act
        publish.accept(publisher);

        // Assert
        JpaOutboxEvent stored = storedRow();
        assertThat(stored.eventType()).isEqualTo(eventType);
        assertThat(stored.routingKey()).isEqualTo(routingKey);
    }

    @ParameterizedTest(name = "{0} is stored pending")
    @MethodSource("appointmentEvents")
    void storesEventAsPendingRowWithRequestCorrelationId(String eventType, String routingKey,
                                                         Consumer<OutboxAppointmentEventPublisher> publish) {
        // Arrange
        MDC.put("correlation.id", "correlation-123");

        // Act
        publish.accept(publisher);

        // Assert
        JpaOutboxEvent stored = storedRow();
        assertThat(stored.eventId()).isNotNull();
        assertThat(stored.correlationId()).isEqualTo("correlation-123");
        assertThat(stored.status()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(stored.retryCount()).isZero();
    }

    @ParameterizedTest(name = "{0} payload names the appointment")
    @MethodSource("appointmentEvents")
    void serializesAppointmentIdIntoPayload(String eventType, String routingKey,
                                            Consumer<OutboxAppointmentEventPublisher> publish) {
        // Act
        publish.accept(publisher);

        // Assert
        assertThat(storedRow().payload())
                .contains("\"appointmentId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\"")
                .contains("\"eventType\":\"" + eventType + "\"");
    }

    @Test
    void serializesAppointmentTypeIntoCompletedPayload() {
        // Act
        publisher.publish(completed());

        // Assert
        assertThat(storedRow().payload()).contains("\"appointmentType\":\"ONLINE\"");
    }

    @Test
    void propagatesSerializationFailure() throws Exception {
        // Arrange
        OutboxAppointmentEventPublisher failingPublisher = publisherWithFailingSerializer();

        // Act
        Throwable thrown = catchThrowable(() -> failingPublisher.publish(scheduled()));

        // Assert
        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not serialize appointment event for the outbox")
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    void writesNoOutboxRowWhenSerializationFails() throws Exception {
        // Arrange
        OutboxAppointmentEventPublisher failingPublisher = publisherWithFailingSerializer();

        // Act
        catchThrowable(() -> failingPublisher.publish(scheduled()));

        // Assert
        verify(repository, never()).save(any());
    }

    private JpaOutboxEvent storedRow() {
        ArgumentCaptor<JpaOutboxEvent> captor = ArgumentCaptor.forClass(JpaOutboxEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private OutboxAppointmentEventPublisher publisherWithFailingSerializer() throws JsonProcessingException {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("broken") { });
        return new OutboxAppointmentEventPublisher(
                repository, failingMapper, Mappers.getMapper(AppointmentEventMapper.class));
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
