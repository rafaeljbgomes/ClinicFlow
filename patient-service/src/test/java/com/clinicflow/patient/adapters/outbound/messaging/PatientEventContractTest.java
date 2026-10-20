package com.clinicflow.patient.adapters.outbound.messaging;

import com.clinicflow.patient.domain.EmailAddress;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PsychologistId;
import com.clinicflow.patient.domain.events.PatientCreatedEvent;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PatientEventContractTest {
    private final ObjectMapper json = JsonMapper.builder().findAndAddModules()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private final PatientEventMapper mapper = Mappers.getMapper(PatientEventMapper.class);

    @Test
    void producerMatchesTheCanonicalPatientCreatedContract() throws Exception {
        // Arrange
        var event = new PatientCreatedEvent(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "PatientCreated", Instant.parse("2026-06-01T10:00:00Z"),
                new PatientId(UUID.fromString("22222222-2222-2222-2222-222222222222")),
                new PsychologistId(UUID.fromString("33333333-3333-3333-3333-333333333333")),
                new EmailAddress("patient@example.com"));
        JsonNode expected = json.readTree(getClass().getResourceAsStream("/contracts/patient-created.json"));

        // Act
        PatientCreatedPayload payload = mapper.toPayload(event);

        // Assert
        assertThat((JsonNode) json.valueToTree(payload)).isEqualTo(expected);
    }
}
