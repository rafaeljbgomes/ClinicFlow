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
    @Test
    void preservesTheExistingRabbitPayloadShape() throws Exception {
        PatientCreatedEvent event = PatientCreatedEvent.of(
                new PatientId(UUID.randomUUID()),
                new PsychologistId(UUID.randomUUID()),
                new EmailAddress("patient@example.com")
        );

        PatientCreatedPayload payload = Mappers.getMapper(PatientEventMapper.class).toPayload(event);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(payload);

        assertThat(json).contains("\"eventId\"", "\"eventType\":\"PatientCreated\"",
                "\"patientId\"", "\"psychologistId\"", "\"patientEmail\":\"patient@example.com\"");
        assertThat(json).doesNotContain("\"value\"");
    }
}
