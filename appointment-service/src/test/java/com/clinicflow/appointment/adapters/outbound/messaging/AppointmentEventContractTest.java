package com.clinicflow.appointment.adapters.outbound.messaging;

import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.AppointmentType;
import com.clinicflow.appointment.domain.CancellationActor;
import com.clinicflow.appointment.domain.CancellationReason;
import com.clinicflow.appointment.domain.DomainEventId;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;
import com.clinicflow.appointment.domain.events.AppointmentCancelledEvent;
import com.clinicflow.appointment.domain.events.AppointmentCompletedEvent;
import com.clinicflow.appointment.domain.events.AppointmentRescheduledEvent;
import com.clinicflow.appointment.domain.events.AppointmentScheduledEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentEventContractTest {
    private static final Instant OCCURRED_AT = Instant.parse("2026-06-01T10:00:00Z");
    private static final AppointmentId APPOINTMENT_ID = new AppointmentId(
            UUID.fromString("44444444-4444-4444-4444-444444444444"));
    private static final PatientId PATIENT_ID = new PatientId(
            UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final PsychologistId PSYCHOLOGIST_ID = new PsychologistId(
            UUID.fromString("33333333-3333-3333-3333-333333333333"));

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final AppointmentEventMapper mapper = Mappers.getMapper(AppointmentEventMapper.class);

    @Test
    void producerMatchesEveryCanonicalAppointmentContract() throws Exception {
        assertContract("appointment-scheduled.json", mapper.toPayload(new AppointmentScheduledEvent(
                eventId("11111111-1111-1111-1111-111111111112"), "AppointmentScheduled", OCCURRED_AT,
                APPOINTMENT_ID, PATIENT_ID, PSYCHOLOGIST_ID,
                new AppointmentDate(Instant.parse("2026-06-02T10:00:00Z")))));

        assertContract("appointment-rescheduled.json", mapper.toPayload(new AppointmentRescheduledEvent(
                eventId("11111111-1111-1111-1111-111111111113"), "AppointmentRescheduled", OCCURRED_AT,
                APPOINTMENT_ID, PATIENT_ID, PSYCHOLOGIST_ID,
                new AppointmentDate(Instant.parse("2026-06-02T10:00:00Z")),
                new AppointmentDate(Instant.parse("2026-06-03T10:00:00Z")))));

        assertContract("appointment-cancelled.json", mapper.toPayload(new AppointmentCancelledEvent(
                eventId("11111111-1111-1111-1111-111111111114"), "AppointmentCancelled", OCCURRED_AT,
                APPOINTMENT_ID, PATIENT_ID, PSYCHOLOGIST_ID,
                new CancellationActor("PSYCHOLOGIST"),
                new CancellationReason("Patient requested a different date"))));

        assertContract("appointment-completed.json", mapper.toPayload(new AppointmentCompletedEvent(
                eventId("11111111-1111-1111-1111-111111111115"), "AppointmentCompleted", OCCURRED_AT,
                APPOINTMENT_ID, PATIENT_ID, PSYCHOLOGIST_ID,
                new AppointmentDate(Instant.parse("2026-06-01T09:00:00Z")), AppointmentType.ONLINE)));
    }

    private void assertContract(String resource, Object payload) throws Exception {
        JsonNode actual = json.valueToTree(payload);
        assertThat(actual)
                .isEqualTo(json.readTree(getClass().getResourceAsStream("/contracts/" + resource)));
    }

    private DomainEventId eventId(String value) {
        return new DomainEventId(UUID.fromString(value));
    }
}
