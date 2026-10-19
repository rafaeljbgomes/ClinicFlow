package com.clinicflow.patient.adapters.outbound.messaging;

import com.clinicflow.patient.domain.EmailAddress;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PsychologistId;
import com.clinicflow.patient.domain.events.PatientCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PatientEventMapperTest {
    private final PatientEventMapper mapper = Mappers.getMapper(PatientEventMapper.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializedPayloadKeepsTheExistingRabbitFields() throws Exception {
        // Arrange
        PatientCreatedEvent event = event();

        // Act
        String payload = json.writeValueAsString(mapper.toPayload(event));

        // Assert
        assertThat(payload).contains("\"eventId\"", "\"eventType\":\"PatientCreated\"",
                "\"patientId\"", "\"psychologistId\"", "\"patientEmail\":\"patient@example.com\"");
    }

    @Test
    void serializedPayloadFlattensValueObjects() throws Exception {
        // Arrange
        PatientCreatedEvent event = event();

        // Act
        String payload = json.writeValueAsString(mapper.toPayload(event));

        // Assert
        assertThat(payload).doesNotContain("\"value\"");
    }

    private PatientCreatedEvent event() {
        return PatientCreatedEvent.of(
                new PatientId(UUID.randomUUID()),
                new PsychologistId(UUID.randomUUID()),
                new EmailAddress("patient@example.com")
        );
    }
}
