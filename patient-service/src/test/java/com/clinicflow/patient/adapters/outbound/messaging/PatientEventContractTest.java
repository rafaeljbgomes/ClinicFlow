package com.clinicflow.patient.adapters.outbound.messaging;

import com.clinicflow.patient.domain.EmailAddress;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PsychologistId;
import com.clinicflow.patient.domain.events.PatientCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PatientEventContractTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final PatientEventMapper mapper = Mappers.getMapper(PatientEventMapper.class);

    @Test
    void producerMatchesTheCanonicalPatientCreatedContract() throws Exception {
        var event = new PatientCreatedEvent(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "PatientCreated", Instant.parse("2026-06-01T10:00:00Z"),
                new PatientId(UUID.fromString("22222222-2222-2222-2222-222222222222")),
                new PsychologistId(UUID.fromString("33333333-3333-3333-3333-333333333333")),
                new EmailAddress("patient@example.com"));

        var expected = json.readTree(getClass().getResourceAsStream("/contracts/patient-created.json"));
        var actual = json.valueToTree(mapper.toPayload(event));

        assertThat(actual).isEqualTo(expected);
    }
}
