package com.clinicflow.notification.adapters.inbound.messaging;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEventMapperTest {
    @Test
    void convertsTypedRabbitPayloadToApplicationCommand() {
        UUID eventId = UUID.randomUUID();
        UUID psychologistId = UUID.randomUUID();
        InboundNotificationEvent payload = new InboundNotificationEvent(
                eventId, "FutureEventType", psychologistId, "PATIENT@Example.COM");

        var command = Mappers.getMapper(NotificationEventMapper.class).toCommand(payload);

        assertThat(command.eventId().value()).isEqualTo(eventId);
        assertThat(command.eventType().value()).isEqualTo("FutureEventType");
        assertThat(command.psychologistId().value()).isEqualTo(psychologistId);
        assertThat(command.recipient().value()).isEqualTo("patient@example.com");
    }
}
