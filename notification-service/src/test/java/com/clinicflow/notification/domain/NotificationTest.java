package com.clinicflow.notification.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {
    @Test
    void createsSentNotificationAndReturnsDefensiveCopies() {
        Instant now = Instant.parse("2026-05-26T10:00:00Z");
        Notification notification = Notification.sent(
                new NotificationId(UUID.randomUUID()),
                new EventId(UUID.randomUUID()),
                new EventType("AppointmentScheduled"),
                new PsychologistId(UUID.randomUUID()),
                new RecipientAddress("patient@example.com"),
                new NotificationSubject("Subject"),
                new NotificationMessage("Message"),
                now
        );

        assertThat(notification.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.sentAt()).isEqualTo(now);
        assertThat(notification.id()).isNotSameAs(notification.id());
        assertThat(notification.eventId()).isNotSameAs(notification.eventId());
        assertThat(notification.eventType()).isNotSameAs(notification.eventType());
        assertThat(notification.subject()).isNotSameAs(notification.subject());
        assertThat(notification.message()).isNotSameAs(notification.message());
        assertThat(notification.failureReason()).isEmpty();
        assertThat(notification).isNotEqualTo(new Object());
    }

    @Test
    void supportsOptionalFieldsAndRejectsEveryMissingRequiredField() {
        Instant now = Instant.parse("2026-05-26T10:00:00Z");
        Object[] fields = {
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
                now,
                null
        };

        Notification optionalFieldsMissing = rehydrate(fields);
        assertThat(optionalFieldsMissing.recipient()).isEmpty();
        assertThat(optionalFieldsMissing.failureReason()).isEmpty();

        int[] requiredIndexes = {0, 1, 2, 4, 5, 7, 8, 10};
        for (int index : requiredIndexes) {
            Object[] invalid = fields.clone();
            invalid[index] = null;
            assertThatThrownBy(() -> rehydrate(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Notification required fields are missing");
        }
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
