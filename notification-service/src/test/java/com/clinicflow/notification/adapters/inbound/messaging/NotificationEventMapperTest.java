package com.clinicflow.notification.adapters.inbound.messaging;

import com.clinicflow.notification.application.commands.ProcessNotificationEventCommand;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEventMapperTest {
    @Test
    void convertsTypedRabbitPayloadToApplicationCommand() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID psychologistId = UUID.randomUUID();
        InboundNotificationEvent payload = new InboundNotificationEvent(
                eventId, "FutureEventType", psychologistId, "PATIENT@Example.COM");
        NotificationEventMapper mapper = Mappers.getMapper(NotificationEventMapper.class);

        // Act
        ProcessNotificationEventCommand command = mapper.toCommand(payload);

        // Assert
        assertThat(command.eventId().value()).isEqualTo(eventId);
        assertThat(command.eventType().value()).isEqualTo("FutureEventType");
        assertThat(command.psychologistId().value()).isEqualTo(psychologistId);
        assertThat(command.recipient().value()).isEqualTo("patient@example.com");
    }
}
