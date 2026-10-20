package com.clinicflow.notification.adapters.inbound.messaging;

import com.clinicflow.notification.application.commands.ProcessNotificationEventCommand;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEventContractTest {
    private final ObjectMapper json = JsonMapper.builder().findAndAddModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final NotificationEventMapper mapper = Mappers.getMapper(NotificationEventMapper.class);

    @ParameterizedTest(name = "consumes {0}")
    @ValueSource(strings = {
            "patient-created.json",
            "appointment-scheduled.json",
            "appointment-rescheduled.json",
            "appointment-cancelled.json"
    })
    void consumerMapsTheCanonicalContractIdentifiers(String resource) throws Exception {
        // Arrange
        InboundNotificationEvent event = readContract(resource);

        // Act
        ProcessNotificationEventCommand command = mapper.toCommand(event);

        // Assert
        assertThat(command.eventId().value()).isNotNull();
        assertThat(command.eventType().value()).isNotBlank();
        assertThat(command.psychologistId()).isNotNull();
    }

    @Test
    void patientCreatedContractCarriesTheRecipient() throws Exception {
        // Arrange
        InboundNotificationEvent event = readContract("patient-created.json");

        // Act
        ProcessNotificationEventCommand command = mapper.toCommand(event);

        // Assert
        assertThat(command.recipient().value()).isEqualTo("patient@example.com");
    }

    @ParameterizedTest(name = "{0} has no recipient")
    @ValueSource(strings = {
            "appointment-scheduled.json",
            "appointment-rescheduled.json",
            "appointment-cancelled.json"
    })
    void appointmentContractsCarryNoRecipient(String resource) throws Exception {
        // Arrange
        InboundNotificationEvent event = readContract(resource);

        // Act
        ProcessNotificationEventCommand command = mapper.toCommand(event);

        // Assert
        assertThat(command.recipient()).isNull();
    }

    private InboundNotificationEvent readContract(String resource) throws Exception {
        return json.readValue(getClass().getResourceAsStream("/contracts/" + resource),
                InboundNotificationEvent.class);
    }
}
