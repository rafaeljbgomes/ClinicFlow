package com.clinicflow.appointment.adapters.outbound.messaging;

import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.AppointmentType;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;
import com.clinicflow.appointment.domain.events.AppointmentCompletedEvent;
import com.clinicflow.appointment.domain.events.AppointmentScheduledEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentEventMapperTest {
    private final AppointmentEventMapper mapper = Mappers.getMapper(AppointmentEventMapper.class);

    @Test
    void preservesTheExistingRabbitPayloadShape() throws Exception {
        AppointmentScheduledEvent event = AppointmentScheduledEvent.of(
                new AppointmentId(UUID.randomUUID()),
                new PatientId(UUID.randomUUID()),
                new PsychologistId(UUID.randomUUID()),
                new AppointmentDate(Instant.parse("2026-05-27T10:00:00Z"))
        );

        AppointmentScheduledPayload payload = mapper.toPayload(event);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(payload);

        assertThat(json).contains("\"eventId\"", "\"eventType\":\"AppointmentScheduled\"",
                "\"appointmentId\"", "\"patientId\"", "\"psychologistId\"", "\"appointmentDate\"");
        assertThat(json).doesNotContain("\"value\"");
    }

    @Test
    void mapsCompletedEventToScalarPayload() {
        AppointmentCompletedEvent event = AppointmentCompletedEvent.of(
                new AppointmentId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")),
                new PatientId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")),
                new PsychologistId(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")),
                new AppointmentDate(Instant.parse("2099-07-01T10:00:00Z")),
                AppointmentType.ONLINE
        );

        AppointmentCompletedPayload payload = mapper.toPayload(event);

        assertThat(payload.eventType()).isEqualTo("AppointmentCompleted");
        assertThat(payload.appointmentId()).isEqualTo(event.appointmentId().value());
        assertThat(payload.patientId()).isEqualTo(event.patientId().value());
        assertThat(payload.psychologistId()).isEqualTo(event.psychologistId().value());
        assertThat(payload.appointmentDate()).isEqualTo(event.appointmentDate().value());
        assertThat(payload.appointmentType()).isEqualTo(AppointmentType.ONLINE);
    }
}
