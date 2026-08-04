package com.clinicflow.clinical.adapters.inbound.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentCompletedEventContractTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final AppointmentCompletedEventMapper mapper = Mappers.getMapper(AppointmentCompletedEventMapper.class);

    @Test
    void consumerDeserializesTheCanonicalCompletedAppointmentContract() throws Exception {
        var event = json.readValue(getClass().getResourceAsStream("/contracts/appointment-completed.json"),
                InboundAppointmentCompletedEvent.class);
        var command = mapper.toCommand(event);

        assertThat(command.eventId().value().toString()).isEqualTo("11111111-1111-1111-1111-111111111115");
        assertThat(command.appointmentId().value().toString()).isEqualTo("44444444-4444-4444-4444-444444444444");
        assertThat(command.modality().name()).isEqualTo("ONLINE");
    }
}
