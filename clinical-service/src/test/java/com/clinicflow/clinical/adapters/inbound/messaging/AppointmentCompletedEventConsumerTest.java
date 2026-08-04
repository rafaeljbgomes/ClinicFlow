package com.clinicflow.clinical.adapters.inbound.messaging;

import com.clinicflow.clinical.application.ClinicalService;
import com.clinicflow.clinical.application.commands.ProcessAppointmentCompletedCommand;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AppointmentCompletedEventConsumerTest {
    private final ClinicalService clinicalService = mock(ClinicalService.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AppointmentCompletedEventConsumer consumer = new AppointmentCompletedEventConsumer(
            clinicalService, Mappers.getMapper(AppointmentCompletedEventMapper.class), registry);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void convertsPayloadAndRecordsSuccessfulConsumption() {
        InboundAppointmentCompletedEvent event = new InboundAppointmentCompletedEvent(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "AppointmentCompleted",
                Instant.parse("2026-06-01T10:00:00Z"),
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                Instant.parse("2026-06-01T09:00:00Z"),
                "ONLINE"
        );
        MessageProperties properties = new MessageProperties();
        properties.setHeader("X-Correlation-Id", "correlation-123".getBytes(StandardCharsets.UTF_8));

        consumer.consume(event, new Message(new byte[0], properties));

        verify(clinicalService).processAppointmentCompleted(any(ProcessAppointmentCompletedCommand.class));
        assertThat(registry.get("clinicflow.domain.events.consumed")
                .tag("service", "clinical-service")
                .tag("event_type", "AppointmentCompleted")
                .tag("status", "success")
                .counter().count()).isEqualTo(1);
        assertThat(MDC.get("correlation.id")).isNull();
    }
}
