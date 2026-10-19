package com.clinicflow.appointment.domain;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class AppointmentTest {
    private static final Instant NOW = Instant.parse("2026-05-26T10:00:00Z");

    @Test
    void cancelReturnsTheSameEntityInstance() {
        // Arrange
        Appointment appointment = appointment();

        // Act
        Appointment cancelled = appointment.cancel(
                new CancellationReason("Patient requested cancellation"), NOW.plusSeconds(60));

        // Assert
        assertThat(cancelled).isSameAs(appointment);
    }

    @Test
    void cancelMarksAppointmentCancelled() {
        // Arrange
        Appointment appointment = appointment();

        // Act
        appointment.cancel(new CancellationReason("Patient requested cancellation"), NOW.plusSeconds(60));

        // Assert
        assertThat(appointment.status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void cancelledAppointmentCannotBeCompleted() {
        // Arrange
        Appointment appointment = appointment();
        appointment.cancel(new CancellationReason("Patient requested cancellation"), NOW.plusSeconds(60));

        // Act
        Throwable thrown = catchThrowable(() -> appointment.complete(NOW.plusSeconds(120)));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void completedAppointmentCannotBeCancelled() {
        // Arrange
        Appointment completed = appointment().complete(NOW.plusSeconds(60));

        // Act
        Throwable thrown = catchThrowable(() -> completed.cancel(
                new CancellationReason("Too late"), NOW.plusSeconds(120)));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rescheduleRejectsDateBeforeTheChangeTime() {
        // Arrange
        Appointment appointment = appointment();

        // Act
        Throwable thrown = catchThrowable(() -> appointment.reschedule(
                new AppointmentDate(NOW.minusSeconds(1)), NOW.plusSeconds(60)));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectedRescheduleDoesNotPartiallyMutateEntity() {
        // Arrange
        Appointment appointment = appointment();
        AppointmentDate originalDate = appointment.scheduledAt();

        // Act
        catchThrowable(() -> appointment.reschedule(new AppointmentDate(NOW.minusSeconds(1)), NOW.plusSeconds(60)));

        // Assert
        assertThat(appointment.scheduledAt()).isEqualTo(originalDate);
        assertThat(appointment.status()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(appointment.updatedAt()).isEqualTo(NOW);
    }

    static Stream<Arguments> valueObjectAccessors() {
        return Stream.of(
                Arguments.of("id", (Function<Appointment, Object>) Appointment::id),
                Arguments.of("psychologist id", (Function<Appointment, Object>) Appointment::psychologistId),
                Arguments.of("patient id", (Function<Appointment, Object>) Appointment::patientId),
                Arguments.of("scheduled at", (Function<Appointment, Object>) Appointment::scheduledAt));
    }

    @ParameterizedTest(name = "{0} returns a defensive copy")
    @MethodSource("valueObjectAccessors")
    void accessorReturnsDefensiveValueObjectCopy(String accessor, Function<Appointment, Object> read) {
        // Arrange
        Appointment appointment = appointment();

        // Act
        Object firstRead = read.apply(appointment);
        Object secondRead = read.apply(appointment);

        // Assert
        assertThat(firstRead).isNotSameAs(secondRead);
    }

    @Test
    void newAppointmentHasNoCancellationReason() {
        // Act
        Appointment appointment = appointment();

        // Assert
        assertThat(appointment.cancellationReason()).isEmpty();
    }

    @Test
    void appointmentIsNotEqualToAnotherType() {
        // Arrange
        Appointment appointment = appointment();

        // Act
        boolean equal = appointment.equals(new Object());

        // Assert
        assertThat(equal).isFalse();
    }

    static Stream<Arguments> requiredFields() {
        return Stream.of(
                Arguments.of("id", 0),
                Arguments.of("psychologist id", 1),
                Arguments.of("patient id", 2),
                Arguments.of("scheduled at", 3),
                Arguments.of("status", 4),
                Arguments.of("type", 5),
                Arguments.of("created at", 7),
                Arguments.of("updated at", 8));
    }

    @ParameterizedTest(name = "rejects missing {0}")
    @MethodSource("requiredFields")
    void rehydrateRejectsMissingRequiredField(String field, int index) {
        // Arrange
        Object[] fields = {
                new AppointmentId(UUID.randomUUID()),
                new PsychologistId(UUID.randomUUID()),
                new PatientId(UUID.randomUUID()),
                new AppointmentDate(NOW.plusSeconds(3600)),
                AppointmentStatus.SCHEDULED,
                AppointmentType.ONLINE,
                null,
                NOW,
                NOW
        };
        fields[index] = null;

        // Act
        Throwable thrown = catchThrowable(() -> rehydrate(fields));

        // Assert
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Appointment required fields are missing");
    }

    static Stream<Arguments> invalidMutations() {
        return Stream.of(
                Arguments.of("reschedule without a date",
                        (Function<Appointment, ThrowingCallable>) appointment ->
                                () -> appointment.reschedule(null, NOW.plusSeconds(1))),
                Arguments.of("cancel without a reason",
                        (Function<Appointment, ThrowingCallable>) appointment ->
                                () -> appointment.cancel(null, NOW.plusSeconds(1))),
                Arguments.of("complete without a time",
                        (Function<Appointment, ThrowingCallable>) appointment ->
                                () -> appointment.complete(null)),
                Arguments.of("complete before the last update",
                        (Function<Appointment, ThrowingCallable>) appointment ->
                                () -> appointment.complete(NOW.minusSeconds(1))));
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidMutations")
    void rejectsInvalidMutationArgument(String scenario, Function<Appointment, ThrowingCallable> mutation) {
        // Arrange
        Appointment appointment = appointment();

        // Act
        Throwable thrown = catchThrowable(mutation.apply(appointment));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    private Appointment appointment() {
        return Appointment.create(new AppointmentId(UUID.randomUUID()), new PsychologistId(UUID.randomUUID()),
                new PatientId(UUID.randomUUID()), new AppointmentDate(NOW.plusSeconds(3600)),
                AppointmentType.ONLINE, NOW);
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
