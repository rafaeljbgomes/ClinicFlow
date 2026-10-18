package com.clinicflow.appointment.adapters.outbound.messaging;

import com.clinicflow.appointment.application.ports.AppointmentEventPublisher;
import com.clinicflow.appointment.domain.events.AppointmentCancelledEvent;
import com.clinicflow.appointment.domain.events.AppointmentCompletedEvent;
import com.clinicflow.appointment.domain.events.AppointmentRescheduledEvent;
import com.clinicflow.appointment.domain.events.AppointmentScheduledEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class OutboxAppointmentEventPublisher implements AppointmentEventPublisher {
    static final String SCHEDULED_ROUTING_KEY = "appointment.scheduled";
    static final String RESCHEDULED_ROUTING_KEY = "appointment.rescheduled";
    static final String CANCELLED_ROUTING_KEY = "appointment.cancelled";
    static final String COMPLETED_ROUTING_KEY = "appointment.completed";

    private final SpringDataOutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final AppointmentEventMapper mapper;

    public OutboxAppointmentEventPublisher(SpringDataOutboxEventRepository repository,
                                           ObjectMapper objectMapper,
                                           AppointmentEventMapper mapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.mapper = mapper;
    }

    @Override
    public void publish(AppointmentScheduledEvent event) {
        save(event.eventId().value(), event.eventType(), SCHEDULED_ROUTING_KEY, mapper.toPayload(event));
    }

    @Override
    public void publish(AppointmentRescheduledEvent event) {
        save(event.eventId().value(), event.eventType(), RESCHEDULED_ROUTING_KEY, mapper.toPayload(event));
    }

    @Override
    public void publish(AppointmentCancelledEvent event) {
        save(event.eventId().value(), event.eventType(), CANCELLED_ROUTING_KEY, mapper.toPayload(event));
    }

    @Override
    public void publish(AppointmentCompletedEvent event) {
        save(event.eventId().value(), event.eventType(), COMPLETED_ROUTING_KEY, mapper.toPayload(event));
    }

    private void save(UUID eventId, String eventType, String routingKey, Object payload) {
        repository.save(JpaOutboxEvent.pending(
                eventId,
                eventType,
                routingKey,
                serialize(payload),
                MDC.get("correlation.id"),
                Instant.now()
        ));
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize appointment event for the outbox", exception);
        }
    }
}
