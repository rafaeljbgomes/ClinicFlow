package com.clinicflow.patient.adapters.outbound.messaging;

import com.clinicflow.patient.domain.EmailAddress;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PsychologistId;
import com.clinicflow.patient.domain.events.PatientCreatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.slf4j.MDC;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPatientEventPublisherTest {
    private final SpringDataOutboxEventRepository repository = mock(SpringDataOutboxEventRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final OutboxPatientEventPublisher publisher = new OutboxPatientEventPublisher(
            repository, objectMapper, Mappers.getMapper(PatientEventMapper.class));

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void storesMappedEventAndRequestCorrelationId() {
        PatientCreatedEvent event = event();
        MDC.put("correlation.id", "correlation-123");

        publisher.publish(event);

        ArgumentCaptor<JpaOutboxEvent> captor = ArgumentCaptor.forClass(JpaOutboxEvent.class);
        verify(repository).save(captor.capture());
        JpaOutboxEvent stored = captor.getValue();
        assertThat(stored.eventId()).isEqualTo(event.eventId());
        assertThat(stored.eventType()).isEqualTo("PatientCreated");
        assertThat(stored.routingKey()).isEqualTo("patient.created");
        assertThat(stored.correlationId()).isEqualTo("correlation-123");
        assertThat(stored.status()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(stored.retryCount()).isZero();
        assertThat(stored.payload())
                .contains("\"eventId\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\"")
                .contains("\"patientEmail\":\"patient@example.com\"");
    }

    @Test
    void propagatesSerializationFailureWithoutWritingOutboxRow() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("broken") { });
        var failingPublisher = new OutboxPatientEventPublisher(
                repository, failingMapper, Mappers.getMapper(PatientEventMapper.class));

        assertThatThrownBy(() -> failingPublisher.publish(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not serialize patient event for the outbox")
                .hasCauseInstanceOf(JsonProcessingException.class);

        verify(repository, never()).save(any());
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
