package com.clinicflow.patient.adapters.outbound.messaging;

import com.clinicflow.patient.domain.EmailAddress;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PsychologistId;
import com.clinicflow.patient.domain.events.PatientCreatedEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.slf4j.MDC;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPatientEventPublisherTest {
    private final SpringDataOutboxEventRepository repository = mock(SpringDataOutboxEventRepository.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules()
            .build();
    private final OutboxPatientEventPublisher publisher = new OutboxPatientEventPublisher(
            repository, objectMapper, Mappers.getMapper(PatientEventMapper.class));

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void storesEventWithItsIdentityTypeAndRoutingKey() {
        // Arrange
        PatientCreatedEvent event = event();

        // Act
        publisher.publish(event);

        // Assert
        JpaOutboxEvent stored = storedRow();
        assertThat(stored.eventId()).isEqualTo(event.eventId());
        assertThat(stored.eventType()).isEqualTo("PatientCreated");
        assertThat(stored.routingKey()).isEqualTo("patient.created");
    }

    @Test
    void storesEventAsPendingRowWithRequestCorrelationId() {
        // Arrange
        MDC.put("correlation.id", "correlation-123");

        // Act
        publisher.publish(event());

        // Assert
        JpaOutboxEvent stored = storedRow();
        assertThat(stored.correlationId()).isEqualTo("correlation-123");
        assertThat(stored.status()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(stored.retryCount()).isZero();
    }

    @Test
    void serializesMappedEventIntoPayload() {
        // Act
        publisher.publish(event());

        // Assert
        assertThat(storedRow().payload())
                .contains("\"eventId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\"")
                .contains("\"patientEmail\":\"patient@example.com\"");
    }

    @Test
    void propagatesSerializationFailure() throws Exception {
        // Arrange
        OutboxPatientEventPublisher failingPublisher = publisherWithFailingSerializer();

        // Act
        Throwable thrown = catchThrowable(() -> failingPublisher.publish(event()));

        // Assert
        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not serialize patient event for the outbox")
                .hasCauseInstanceOf(JacksonException.class);
    }

    @Test
    void writesNoOutboxRowWhenSerializationFails() throws Exception {
        // Arrange
        OutboxPatientEventPublisher failingPublisher = publisherWithFailingSerializer();

        // Act
        catchThrowable(() -> failingPublisher.publish(event()));

        // Assert
        verify(repository, never()).save(any());
    }

    private JpaOutboxEvent storedRow() {
        ArgumentCaptor<JpaOutboxEvent> captor = ArgumentCaptor.forClass(JpaOutboxEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private OutboxPatientEventPublisher publisherWithFailingSerializer() {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new JacksonException("broken") { });
        return new OutboxPatientEventPublisher(
                repository, failingMapper, Mappers.getMapper(PatientEventMapper.class));
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
