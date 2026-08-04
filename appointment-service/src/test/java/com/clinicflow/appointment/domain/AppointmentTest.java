package com.clinicflow.appointment.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentTest {
    @Test
    void mutatesTheSameInstanceAndPreventsTerminalStateChanges() {
        Instant now = Instant.parse("2026-05-26T10:00:00Z");
        Appointment appointment = appointment(now);

        Appointment cancelled = appointment.cancel(
                new CancellationReason("Patient requested cancellation"), now.plusSeconds(60));

        assertThat(cancelled).isSameAs(appointment);
        assertThat(appointment.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThatThrownBy(() -> appointment.complete(now.plusSeconds(120)))
                .isInstanceOf(IllegalStateException.class);

        Appointment completed = appointment(now).complete(now.plusSeconds(60));
        assertThatThrownBy(() -> completed.cancel(
                new CancellationReason("Too late"), now.plusSeconds(120)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidRescheduleDoesNotPartiallyMutateEntity() {
        Instant now = Instant.parse("2026-05-26T10:00:00Z");
        Appointment appointment = appointment(now);
        AppointmentDate originalDate = appointment.scheduledAt();

        assertThatThrownBy(() -> appointment.reschedule(
                new AppointmentDate(now.minusSeconds(1)), now.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(appointment.scheduledAt()).isEqualTo(originalDate);
        assertThat(appointment.status()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(appointment.updatedAt()).isEqualTo(now);
    }

    @Test
    void returnsDefensiveValueObjectCopies() {
        Appointment appointment = appointment(Instant.parse("2026-05-26T10:00:00Z"));

        assertThat(appointment.id()).isNotSameAs(appointment.id());
        assertThat(appointment.psychologistId()).isNotSameAs(appointment.psychologistId());
        assertThat(appointment.patientId()).isNotSameAs(appointment.patientId());
        assertThat(appointment.scheduledAt()).isNotSameAs(appointment.scheduledAt());
        assertThat(appointment.cancellationReason()).isEmpty();
        assertThat(appointment).isNotEqualTo(new Object());
    }

    @Test
    void rejectsEveryMissingRequiredField() {
        Instant now = Instant.parse("2026-05-26T10:00:00Z");
        Object[] fields = {
                new AppointmentId(UUID.randomUUID()),
                new PsychologistId(UUID.randomUUID()),
                new PatientId(UUID.randomUUID()),
                new AppointmentDate(now.plusSeconds(3600)),
                AppointmentStatus.SCHEDULED,
                AppointmentType.ONLINE,
                null,
                now,
                now
        };

        int[] requiredIndexes = {0, 1, 2, 3, 4, 5, 7, 8};
        for (int index : requiredIndexes) {
            Object[] invalid = fields.clone();
            invalid[index] = null;
            assertThatThrownBy(() -> rehydrate(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Appointment required fields are missing");
        }
    }

    @Test
    void rejectsInvalidMutationArgumentsAndTimes() {
        Instant now = Instant.parse("2026-05-26T10:00:00Z");
        Appointment appointment = appointment(now);

        assertThatThrownBy(() -> appointment.reschedule(null, now.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> appointment.cancel(null, now.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> appointment.complete(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> appointment.complete(now.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Appointment appointment(Instant now) {
        return Appointment.create(new AppointmentId(UUID.randomUUID()), new PsychologistId(UUID.randomUUID()),
                new PatientId(UUID.randomUUID()), new AppointmentDate(now.plusSeconds(3600)),
                AppointmentType.ONLINE, now);
    }

    private Appointment rehydrate(Object[] fields) {
        return Appointment.rehydrate(
                (AppointmentId) fields[0],
                (PsychologistId) fields[1],
                (PatientId) fields[2],
                (AppointmentDate) fields[3],
                (AppointmentStatus) fields[4],
                (AppointmentType) fields[5],
                (CancellationReason) fields[6],
                (Instant) fields[7],
                (Instant) fields[8]);
    }
}
