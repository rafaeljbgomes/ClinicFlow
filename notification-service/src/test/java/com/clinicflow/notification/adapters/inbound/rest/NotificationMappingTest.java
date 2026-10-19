package com.clinicflow.notification.adapters.inbound.rest;

import com.clinicflow.notification.adapters.inbound.rest.dto.NotificationResponse;
import com.clinicflow.notification.application.mapping.NotificationApplicationMapper;
import com.clinicflow.notification.application.queries.GetNotificationQuery;
import com.clinicflow.notification.application.results.NotificationResult;
import com.clinicflow.notification.domain.EventId;
import com.clinicflow.notification.domain.EventType;
import com.clinicflow.notification.domain.Notification;
import com.clinicflow.notification.domain.NotificationId;
import com.clinicflow.notification.domain.NotificationMessage;
import com.clinicflow.notification.domain.NotificationStatus;
import com.clinicflow.notification.domain.NotificationSubject;
import com.clinicflow.notification.domain.NotificationType;
import com.clinicflow.notification.domain.PsychologistId;
import com.clinicflow.notification.domain.RecipientAddress;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMappingTest {
    private static final UUID NOTIFICATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID EVENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant NOW = Instant.parse("2026-05-26T10:00:00Z");

    private final NotificationRestMapper restMapper = Mappers.getMapper(NotificationRestMapper.class);
    private final NotificationApplicationMapper applicationMapper =
            Mappers.getMapper(NotificationApplicationMapper.class);

    @Test
    void notificationIdMapsToQuery() {
        // Act
        GetNotificationQuery query = restMapper.toQuery(NOTIFICATION_ID);

        // Assert
        assertThat(query.notificationId()).isEqualTo(new NotificationId(NOTIFICATION_ID));
    }

    @Test
    void notificationMapsToApplicationResult() {
        // Arrange
        Notification notification = Notification.sent(
                new NotificationId(NOTIFICATION_ID),
                new EventId(EVENT_ID),
                new EventType("PatientCreated"),
                new PsychologistId(UUID.randomUUID()),
                new RecipientAddress("patient@example.com"),
                new NotificationSubject("Patient Created"),
                new NotificationMessage("Patient was created"),
                NOW
        );

        // Act
        NotificationResult result = applicationMapper.toResult(notification);

        // Assert
        assertThat(result.id()).isEqualTo(NOTIFICATION_ID);
        assertThat(result.eventId()).isEqualTo(EVENT_ID);
        assertThat(result.eventType()).isEqualTo("PatientCreated");
        assertThat(result.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(result.recipient()).isEqualTo("patient@example.com");
    }

    @Test
    void applicationResultMapsToResponse() {
        // Arrange
        NotificationResult result = new NotificationResult(NOTIFICATION_ID, EVENT_ID, "PatientCreated",
                UUID.randomUUID(), NotificationType.EMAIL, NotificationStatus.SENT, "patient@example.com",
                "Patient Created", NOW, NOW);

        // Act
        NotificationResponse response = restMapper.toResponse(result);

        // Assert
        assertThat(response.id()).isEqualTo(NOTIFICATION_ID);
        assertThat(response.eventId()).isEqualTo(EVENT_ID);
        assertThat(response.eventType()).isEqualTo("PatientCreated");
        assertThat(response.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(response.recipient()).isEqualTo("patient@example.com");
    }
}
