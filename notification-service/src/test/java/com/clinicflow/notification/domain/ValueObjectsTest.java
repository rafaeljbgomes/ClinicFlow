package com.clinicflow.notification.domain;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ValueObjectsTest {
    static Stream<Arguments> identifiers() {
        return Stream.of(
                Arguments.of("notification id", (Function<UUID, UUID>) id -> new NotificationId(id).value()),
                Arguments.of("event id", (Function<UUID, UUID>) id -> new EventId(id).value()));
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
    void eventTypeAcceptsDottedAndHyphenatedNames() {
        // Act
        EventType eventType = new EventType("Custom.Event-1");

        // Assert
        assertThat(eventType.value()).isEqualTo("Custom.Event-1");
    }

    @Test
    void recipientAddressIsTrimmedAndLowerCased() {
        // Act
        RecipientAddress recipient = new RecipientAddress(" PATIENT@Example.COM ");

        // Assert
        assertThat(recipient.value()).isEqualTo("patient@example.com");
    }

    @Test
    void subjectIsTrimmed() {
        // Act
        NotificationSubject subject = new NotificationSubject(" Subject ");

        // Assert
        assertThat(subject.value()).isEqualTo("Subject");
    }

    @Test
    void messageIsTrimmed() {
        // Act
        NotificationMessage message = new NotificationMessage(" Message ");

        // Assert
        assertThat(message.value()).isEqualTo("Message");
    }

    @Test
    void failureReasonIsTrimmed() {
        // Act
        FailureReason reason = new FailureReason(" Failure ");

        // Assert
        assertThat(reason.value()).isEqualTo("Failure");
    }

    static Stream<Arguments> invalidValues() {
        return Stream.of(
                Arguments.of("missing notification id", (ThrowingCallable) () -> new NotificationId(null)),
                Arguments.of("missing event id", (ThrowingCallable) () -> new EventId(null)),
                Arguments.of("missing event type", (ThrowingCallable) () -> new EventType(null)),
                Arguments.of("event type with spaces", (ThrowingCallable) () -> new EventType("bad event")),
                Arguments.of("missing recipient", (ThrowingCallable) () -> new RecipientAddress(null)),
                Arguments.of("malformed recipient", (ThrowingCallable) () -> new RecipientAddress("invalid")),
                Arguments.of("missing subject", (ThrowingCallable) () -> new NotificationSubject(null)),
                Arguments.of("blank subject", (ThrowingCallable) () -> new NotificationSubject(" ")),
                Arguments.of("missing message", (ThrowingCallable) () -> new NotificationMessage(null)),
                Arguments.of("blank message", (ThrowingCallable) () -> new NotificationMessage(" ")),
                Arguments.of("missing failure reason", (ThrowingCallable) () -> new FailureReason(null)),
                Arguments.of("blank failure reason", (ThrowingCallable) () -> new FailureReason(" ")));
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
