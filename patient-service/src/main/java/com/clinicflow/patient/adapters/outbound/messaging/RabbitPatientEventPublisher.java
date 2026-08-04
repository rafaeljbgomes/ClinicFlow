package com.clinicflow.patient.adapters.outbound.messaging;

import com.clinicflow.patient.application.ports.PatientEventPublisher;
import com.clinicflow.patient.domain.events.PatientCreatedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RabbitPatientEventPublisher implements PatientEventPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final MeterRegistry meterRegistry;
    private final PatientEventMapper mapper;

    public RabbitPatientEventPublisher(RabbitTemplate rabbitTemplate,
                                       @Value("${clinicflow.messaging.exchange}") String exchange,
                                       MeterRegistry meterRegistry,
                                       PatientEventMapper mapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.meterRegistry = meterRegistry;
        this.mapper = mapper;
    }

    @Override
    public void publish(PatientCreatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(exchange, "patient.created", mapper.toPayload(event), message -> {
                String correlationId = MDC.get("correlation.id");
                if (correlationId != null) {
                    message.getMessageProperties().setHeader("X-Correlation-Id", correlationId);
                }
                message.getMessageProperties().setMessageId(event.eventId().toString());
                return message;
            });
            recordPublishedEvent("PatientCreated", "success");
        } catch (RuntimeException ex) {
            recordPublishedEvent("PatientCreated", "failure");
            throw ex;
        }
    }

    private void recordPublishedEvent(String eventType, String status) {
        meterRegistry.counter(
                "clinicflow.domain.events.published",
                "service", "patient-service",
                "event_type", eventType,
                "status", status
        ).increment();
    }
}
