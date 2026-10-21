package com.clinicflow.patient.adapters.outbound.messaging;

import com.clinicflow.patient.application.ports.PatientEventPublisher;
import com.clinicflow.patient.domain.events.PatientCreatedEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class OutboxPatientEventPublisher implements PatientEventPublisher {
    static final String PATIENT_CREATED_ROUTING_KEY = "patient.created";

    private final SpringDataOutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final PatientEventMapper mapper;

    public OutboxPatientEventPublisher(SpringDataOutboxEventRepository repository,
                                       ObjectMapper objectMapper,
                                       PatientEventMapper mapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.mapper = mapper;
    }

    @Override
    public void publish(PatientCreatedEvent event) {
        var payload = mapper.toPayload(event);
        repository.save(JpaOutboxEvent.pending(
                event.eventId(),
                event.eventType(),
                PATIENT_CREATED_ROUTING_KEY,
                serialize(payload),
                MDC.get("correlation.id"),
                Instant.now()
        ));
    }

    private String serialize(PatientCreatedPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize patient event for the outbox", exception);
        }
    }
}
