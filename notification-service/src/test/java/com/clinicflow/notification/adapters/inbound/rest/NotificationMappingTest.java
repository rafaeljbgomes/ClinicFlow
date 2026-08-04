package com.clinicflow.notification.adapters.inbound.rest;

import com.clinicflow.notification.application.mapping.NotificationApplicationMapper;
import com.clinicflow.notification.domain.EventId;
import com.clinicflow.notification.domain.EventType;
import com.clinicflow.notification.domain.Notification;
import com.clinicflow.notification.domain.NotificationId;
import com.clinicflow.notification.domain.NotificationMessage;
import com.clinicflow.notification.domain.NotificationStatus;
import com.clinicflow.notification.domain.NotificationSubject;
import com.clinicflow.notification.domain.PsychologistId;
import com.clinicflow.notification.domain.RecipientAddress;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMappingTest {
    private final NotificationRestMapper restMapper = Mappers.getMapper(NotificationRestMapper.class);
    private final NotificationApplicationMapper applicationMapper =
            Mappers.getMapper(NotificationApplicationMapper.class);

    @Test
    void mapsRestInputsAndApplicationOutputs() {
        UUID notificationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.parse("2026-05-26T10:00:00Z");
        Notification notification = Notification.sent(
                new NotificationId(notificationId),
                new EventId(eventId),
                new EventType("PatientCreated"),
                new PsychologistId(UUID.randomUUID()),
                new RecipientAddress("patient@example.com"),
                new NotificationSubject("Patient Created"),
                new NotificationMessage("Patient was created"),
                now
        );

        assertThat(restMapper.toQuery(notificationId).notificationId())
                .isEqualTo(new NotificationId(notificationId));

        var response = restMapper.toResponse(applicationMapper.toResult(notification));

        assertThat(response.id()).isEqualTo(notificationId);
        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.eventType()).isEqualTo("PatientCreated");
        assertThat(response.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(response.recipient()).isEqualTo("patient@example.com");
    }
}
