package com.clinicflow.clinical.adapters.inbound.messaging;

import com.clinicflow.clinical.application.ClinicalService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class AppointmentCompletedEventConsumer {
    private final ClinicalService clinicalService;
    private final AppointmentCompletedEventMapper mapper;
    private final MeterRegistry meterRegistry;

    public AppointmentCompletedEventConsumer(ClinicalService clinicalService,
                                             AppointmentCompletedEventMapper mapper,
                                             MeterRegistry meterRegistry) {
        this.clinicalService = clinicalService;
        this.mapper = mapper;
        this.meterRegistry = meterRegistry;
    }

    @RabbitListener(queues = "${clinicflow.messaging.clinical-queue}")
    public void consume(@Payload InboundAppointmentCompletedEvent payload, Message message) {
        Object header = message.getMessageProperties().getHeaders().get("X-Correlation-Id");
        if (header != null) {
            MDC.put("correlation.id", header instanceof byte[] bytes
                    ? new String(bytes, StandardCharsets.UTF_8) : header.toString());
        }
        String eventType = payload.eventType() == null ? "Unknown" : payload.eventType();
        try {
            clinicalService.processAppointmentCompleted(mapper.toCommand(payload));
            recordConsumedEvent(eventType, "success");
        } catch (RuntimeException ex) {
            recordConsumedEvent(eventType, "failure");
            throw ex;
        } finally {
            MDC.remove("correlation.id");
        }
    }

    private void recordConsumedEvent(String eventType, String status) {
        meterRegistry.counter("clinicflow.domain.events.consumed",
                "service", "clinical-service", "event_type", eventType, "status", status).increment();
    }
}
