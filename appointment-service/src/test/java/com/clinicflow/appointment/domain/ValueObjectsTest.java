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

class ValueObjectsTest {
    static Stream<Arguments> identifiers() {
        return Stream.of(
                Arguments.of("appointment id", (Function<UUID, UUID>) id -> new AppointmentId(id).value()),
                Arguments.of("psychologist id", (Function<UUID, UUID>) id -> new PsychologistId(id).value()),
                Arguments.of("patient id", (Function<UUID, UUID>) id -> new PatientId(id).value()),
                Arguments.of("domain event id", (Function<UUID, UUID>) id -> new DomainEventId(id).value()));
    }

    @ParameterizedTest(name = "{0} wraps the supplied UUID")
    @MethodSource("identifiers")
    void identifierWrapsTheSuppliedUuid(String identifier, Function<UUID, UUID> wrapAndRead) {
        // Arrange
        UUID id = UUID.randomUUID();

        // Act
        UUID wrapped = wrapAndRead.apply(id);

        // Assert
        assertThat(wrapped).isEqualTo(id);
    }

    @Test
    void appointmentDateKeepsTheSuppliedInstant() {
        // Act
        AppointmentDate date = new AppointmentDate(Instant.EPOCH);

        // Assert
        assertThat(date.value()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void cancellationReasonIsTrimmed() {
        // Act
        CancellationReason reason = new CancellationReason("  Reason  ");

        // Assert
        assertThat(reason.value()).isEqualTo("Reason");
    }

    @Test
    void cancellationActorIsTrimmedAndLowerCased() {
        // Act
        CancellationActor actor = new CancellationActor(" Psychologist ");

        // Assert
        assertThat(actor.value()).isEqualTo("psychologist");
    }

    static Stream<Arguments> invalidValues() {
        return Stream.of(
                Arguments.of("missing appointment id", (ThrowingCallable) () -> new AppointmentId(null)),
                Arguments.of("missing psychologist id", (ThrowingCallable) () -> new PsychologistId(null)),
                Arguments.of("missing patient id", (ThrowingCallable) () -> new PatientId(null)),
                Arguments.of("missing domain event id", (ThrowingCallable) () -> new DomainEventId(null)),
                Arguments.of("missing appointment date", (ThrowingCallable) () -> new AppointmentDate(null)),
                Arguments.of("missing cancellation reason", (ThrowingCallable) () -> new CancellationReason(null)),
                Arguments.of("blank cancellation reason", (ThrowingCallable) () -> new CancellationReason(" ")),
                Arguments.of("missing cancellation actor", (ThrowingCallable) () -> new CancellationActor(null)),
                Arguments.of("blank cancellation actor", (ThrowingCallable) () -> new CancellationActor(" ")));
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidValues")
    void rejectsInvalidValue(String scenario, ThrowingCallable construct) {
        // Act
        Throwable thrown = catchThrowable(construct);

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }
}
