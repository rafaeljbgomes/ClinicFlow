package com.clinicflow.notification.domain;

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

class NotificationTest {
    private static final Instant NOW = Instant.parse("2026-05-26T10:00:00Z");

    @Test
    void sentNotificationRecordsSentStatusAndTime() {
        // Act
        Notification notification = sentNotification();

        // Assert
        assertThat(notification.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.sentAt()).isEqualTo(NOW);
    }

    @Test
    void sentNotificationHasNoFailureReason() {
        // Act
        Notification notification = sentNotification();

        // Assert
        assertThat(notification.failureReason()).isEmpty();
    }

    static Stream<Arguments> valueObjectAccessors() {
        return Stream.of(
                Arguments.of("id", (Function<Notification, Object>) Notification::id),
                Arguments.of("event id", (Function<Notification, Object>) Notification::eventId),
                Arguments.of("event type", (Function<Notification, Object>) Notification::eventType),
                Arguments.of("subject", (Function<Notification, Object>) Notification::subject),
                Arguments.of("message", (Function<Notification, Object>) Notification::message));
    }

    @ParameterizedTest(name = "{0} returns a defensive copy")
    @MethodSource("valueObjectAccessors")
    void accessorReturnsDefensiveValueObjectCopy(String accessor, Function<Notification, Object> read) {
        // Arrange
        Notification notification = sentNotification();

        // Act
        Object firstRead = read.apply(notification);
        Object secondRead = read.apply(notification);

        // Assert
        assertThat(firstRead).isNotSameAs(secondRead);
    }

    @Test
    void notificationIsNotEqualToAnotherType() {
        // Arrange
        Notification notification = sentNotification();

        // Act
        boolean equal = notification.equals(new Object());

        // Assert
        assertThat(equal).isFalse();
    }

    @Test
    void rehydrateAcceptsMissingOptionalFields() {
        // Arrange
        Object[] fields = validRehydrationFields();

        // Act
        Notification notification = rehydrate(fields);

        // Assert
        assertThat(notification.recipient()).isEmpty();
        assertThat(notification.failureReason()).isEmpty();
    }

    static Stream<Arguments> requiredFields() {
        return Stream.of(
                Arguments.of("id", 0),
                Arguments.of("event id", 1),
                Arguments.of("event type", 2),
                Arguments.of("type", 4),
                Arguments.of("status", 5),
                Arguments.of("subject", 7),
                Arguments.of("message", 8),
                Arguments.of("created at", 10));
    }

    @ParameterizedTest(name = "rejects missing {0}")
    @MethodSource("requiredFields")
    void rehydrateRejectsMissingRequiredField(String field, int index) {
        // Arrange
        Object[] fields = validRehydrationFields();
        fields[index] = null;

        // Act
        Throwable thrown = catchThrowable(() -> rehydrate(fields));

        // Assert
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notification required fields are missing");
    }

    private Notification sentNotification() {
        return Notification.sent(
                new NotificationId(UUID.randomUUID()),
                new EventId(UUID.randomUUID()),
                new EventType("AppointmentScheduled"),
                new PsychologistId(UUID.randomUUID()),
                new RecipientAddress("patient@example.com"),
                new NotificationSubject("Subject"),
                new NotificationMessage("Message"),
                NOW
        );
    }

    private static Object[] validRehydrationFields() {
        return new Object[] {
                new NotificationId(UUID.randomUUID()),
                new EventId(UUID.randomUUID()),
                new EventType("AppointmentScheduled"),
                new PsychologistId(UUID.randomUUID()),
                NotificationType.EMAIL,
                NotificationStatus.FAILED,
                null,
                new NotificationSubject("Subject"),
                new NotificationMessage("Message"),
                null,
                NOW,
                null
        };
    }

    private Notification rehydrate(Object[] fields) {
        return Notification.rehydrate(
                (NotificationId) fields[0],
                (EventId) fields[1],
                (EventType) fields[2],
                (PsychologistId) fields[3],
                (NotificationType) fields[4],
                (NotificationStatus) fields[5],
                (RecipientAddress) fields[6],
                (NotificationSubject) fields[7],
                (NotificationMessage) fields[8],
                (FailureReason) fields[9],
                (Instant) fields[10],
                (Instant) fields[11]);
    }
}
