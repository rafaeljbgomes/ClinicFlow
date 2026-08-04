package com.clinicflow.notification.adapters.inbound.messaging;

import com.clinicflow.notification.application.NotificationService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class NotificationEventConsumer {
    private final NotificationService notificationService;
    private final NotificationEventMapper mapper;
    private final MeterRegistry meterRegistry;

    public NotificationEventConsumer(NotificationService notificationService,
                                     NotificationEventMapper mapper,
                                     MeterRegistry meterRegistry) {
        this.notificationService = notificationService;
        this.mapper = mapper;
        this.meterRegistry = meterRegistry;
    }

    @RabbitListener(queues = "${clinicflow.messaging.notification-queue}")
    public void consume(@Payload InboundNotificationEvent payload, Message message) {
        Object header = message.getMessageProperties().getHeaders().get("X-Correlation-Id");
        if (header != null) {
            MDC.put("correlation.id", header instanceof byte[] bytes
                    ? new String(bytes, StandardCharsets.UTF_8) : header.toString());
        }
        String eventType = payload.eventType() == null ? "Unknown" : payload.eventType();
        try {
            notificationService.processEvent(mapper.toCommand(payload));
            recordConsumedEvent(eventType, "success");
            recordProcessedNotification(eventType, "success");
        } catch (RuntimeException ex) {
            recordConsumedEvent(eventType, "failure");
            recordProcessedNotification(eventType, "failure");
            throw ex;
        } finally {
            MDC.remove("correlation.id");
        }
    }

    private void recordConsumedEvent(String eventType, String status) {
        meterRegistry.counter("clinicflow.domain.events.consumed",
                "service", "notification-service", "event_type", eventType, "status", status).increment();
    }

    private void recordProcessedNotification(String eventType, String status) {
        meterRegistry.counter("clinicflow.notifications.processed",
                "service", "notification-service", "event_type", eventType, "status", status).increment();
    }
}
