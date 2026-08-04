package com.clinicflow.notification.adapters.inbound.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEventContractTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final NotificationEventMapper mapper = Mappers.getMapper(NotificationEventMapper.class);

    @ParameterizedTest
    @ValueSource(strings = {
            "patient-created.json",
            "appointment-scheduled.json",
            "appointment-rescheduled.json",
            "appointment-cancelled.json"
    })
    void consumerDeserializesEveryCanonicalNotificationContract(String resource) throws Exception {
        var event = json.readValue(getClass().getResourceAsStream("/contracts/" + resource),
                InboundNotificationEvent.class);
        var command = mapper.toCommand(event);

        assertThat(command.eventId().value()).isNotNull();
        assertThat(command.eventType().value()).isNotBlank();
        assertThat(command.psychologistId()).isNotNull();
        if (resource.equals("patient-created.json")) {
            assertThat(command.recipient().value()).isEqualTo("patient@example.com");
        } else {
            assertThat(command.recipient()).isNull();
        }
    }
}
