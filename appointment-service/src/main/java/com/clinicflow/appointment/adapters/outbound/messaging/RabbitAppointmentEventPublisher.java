package com.clinicflow.appointment.adapters.outbound.messaging;

import com.clinicflow.appointment.application.ports.AppointmentEventPublisher;
import com.clinicflow.appointment.domain.events.AppointmentCancelledEvent;
import com.clinicflow.appointment.domain.events.AppointmentCompletedEvent;
import com.clinicflow.appointment.domain.events.AppointmentRescheduledEvent;
import com.clinicflow.appointment.domain.events.AppointmentScheduledEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RabbitAppointmentEventPublisher implements AppointmentEventPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final MeterRegistry meterRegistry;
    private final AppointmentEventMapper mapper;

    public RabbitAppointmentEventPublisher(RabbitTemplate rabbitTemplate,
                                           @Value("${clinicflow.messaging.exchange}") String exchange,
                                           MeterRegistry meterRegistry,
                                           AppointmentEventMapper mapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.meterRegistry = meterRegistry;
        this.mapper = mapper;
    }

    @Override public void publish(AppointmentScheduledEvent event) { send("appointment.scheduled", "AppointmentScheduled", mapper.toPayload(event), event.eventId().value().toString()); }
    @Override public void publish(AppointmentRescheduledEvent event) { send("appointment.rescheduled", "AppointmentRescheduled", mapper.toPayload(event), event.eventId().value().toString()); }
    @Override public void publish(AppointmentCancelledEvent event) { send("appointment.cancelled", "AppointmentCancelled", mapper.toPayload(event), event.eventId().value().toString()); }
    @Override public void publish(AppointmentCompletedEvent event) { send("appointment.completed", "AppointmentCompleted", mapper.toPayload(event), event.eventId().value().toString()); }

    private void send(String routingKey, String eventType, Object event, String eventId) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, event, message -> {
                String correlationId = MDC.get("correlation.id");
                if (correlationId != null) {
                    message.getMessageProperties().setHeader("X-Correlation-Id", correlationId);
                }
                message.getMessageProperties().setMessageId(eventId);
                return message;
            });
            recordPublishedEvent(eventType, "success");
        } catch (RuntimeException ex) {
            recordPublishedEvent(eventType, "failure");
            throw ex;
        }
    }

    private void recordPublishedEvent(String eventType, String status) {
        meterRegistry.counter(
                "clinicflow.domain.events.published",
                "service", "appointment-service",
                "event_type", eventType,
                "status", status
        ).increment();
    }
}
