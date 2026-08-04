package com.clinicflow.notification.adapters.inbound.messaging;

import com.clinicflow.notification.application.NotificationService;
import com.clinicflow.notification.application.commands.ProcessNotificationEventCommand;
import com.clinicflow.notification.domain.EventId;
import com.clinicflow.notification.domain.EventType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationEventConsumerTest {
    private final NotificationService service = mock(NotificationService.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final NotificationEventConsumer consumer = new NotificationEventConsumer(
            service,
            Mappers.getMapper(NotificationEventMapper.class),
            registry
    );

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    void processesEventPropagatesByteHeaderAndRecordsSuccess() {
        doAnswer(invocation -> {
            assertThat(MDC.get("correlation.id")).isEqualTo("correlation-123");
            return null;
        }).when(service).processEvent(any());

        consumer.consume(payload("PatientCreated"),
                message("correlation-123".getBytes(StandardCharsets.UTF_8)));

        verify(service).processEvent(any(ProcessNotificationEventCommand.class));
        assertThat(counter("clinicflow.domain.events.consumed", "PatientCreated", "success")).isEqualTo(1);
        assertThat(counter("clinicflow.notifications.processed", "PatientCreated", "success")).isEqualTo(1);
        assertThat(MDC.get("correlation.id")).isNull();
    }

    @Test
    void recordsServiceFailureAndAlwaysCleansMdc() {
        doThrow(new IllegalStateException("processing failed")).when(service).processEvent(any());

        assertThatThrownBy(() -> consumer.consume(payload("PatientCreated"), message("correlation-456")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(counter("clinicflow.domain.events.consumed", "PatientCreated", "failure")).isEqualTo(1);
        assertThat(counter("clinicflow.notifications.processed", "PatientCreated", "failure")).isEqualTo(1);
        assertThat(MDC.get("correlation.id")).isNull();
    }

    @Test
    void recordsUnknownFailureWhenPayloadCannotBeMapped() {
        assertThatThrownBy(() -> consumer.consume(payload(null), message("correlation-789")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(counter("clinicflow.domain.events.consumed", "Unknown", "failure")).isEqualTo(1);
        assertThat(counter("clinicflow.notifications.processed", "Unknown", "failure")).isEqualTo(1);
        assertThat(MDC.get("correlation.id")).isNull();
    }

    private double counter(String name, String eventType, String status) {
        return registry.get(name)
                .tag("service", "notification-service")
                .tag("event_type", eventType)
                .tag("status", status)
                .counter()
                .count();
    }

    private InboundNotificationEvent payload(String eventType) {
        return new InboundNotificationEvent(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                eventType,
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                "patient@example.com"
        );
    }

    private Message message(Object correlationHeader) {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("X-Correlation-Id", correlationHeader);
        return new Message(new byte[0], properties);
    }
}
