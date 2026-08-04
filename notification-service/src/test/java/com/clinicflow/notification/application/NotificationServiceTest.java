package com.clinicflow.notification.application;

import com.clinicflow.notification.application.commands.ProcessNotificationEventCommand;
import com.clinicflow.notification.application.exceptions.NotificationNotFoundException;
import com.clinicflow.notification.application.mapping.NotificationApplicationMapper;
import com.clinicflow.notification.application.ports.NotificationRepository;
import com.clinicflow.notification.application.queries.GetNotificationQuery;
import com.clinicflow.notification.domain.EventId;
import com.clinicflow.notification.domain.EventType;
import com.clinicflow.notification.domain.Notification;
import com.clinicflow.notification.domain.NotificationId;
import com.clinicflow.notification.domain.NotificationMessage;
import com.clinicflow.notification.domain.NotificationSubject;
import com.clinicflow.notification.domain.PsychologistId;
import com.clinicflow.notification.domain.RecipientAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    private static final EventId EVENT_ID =
            new EventId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private static final NotificationId NOTIFICATION_ID =
            new NotificationId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    private static final PsychologistId PSYCHOLOGIST_ID =
            new PsychologistId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));

    @Mock
    private NotificationRepository repository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository,
                Mappers.getMapper(NotificationApplicationMapper.class),
                new NotificationContentFactory());
    }

    @Test
    void createsNotificationForNewEvent() {
        ProcessNotificationEventCommand command = new ProcessNotificationEventCommand(
                EVENT_ID,
                new EventType("PatientCreated"),
                PSYCHOLOGIST_ID,
                new RecipientAddress("patient@example.com")
        );

        service.processEvent(command);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.eventId()).isEqualTo(EVENT_ID);
        assertThat(saved.recipient()).contains(new RecipientAddress("patient@example.com"));
        assertThat(saved.subject().value()).isEqualTo("Patient profile created");
    }

    @Test
    void skipsDuplicateEventId() {
        when(repository.existsByEventId(EVENT_ID)).thenReturn(true);

        service.processEvent(new ProcessNotificationEventCommand(
                EVENT_ID, new EventType("PatientCreated"), PSYCHOLOGIST_ID, null));

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listsAndGetsNotifications() {
        Notification notification = notification();
        when(repository.findAll()).thenReturn(List.of(notification));
        when(repository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(notification));

        assertThat(service.list()).extracting(result -> result.id())
                .containsExactly(NOTIFICATION_ID.value());
        assertThat(service.get(new GetNotificationQuery(NOTIFICATION_ID)).eventId())
                .isEqualTo(EVENT_ID.value());
    }

    @Test
    void throwsWhenNotificationDoesNotExist() {
        when(repository.findById(NOTIFICATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(new GetNotificationQuery(NOTIFICATION_ID)))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    private Notification notification() {
        return Notification.sent(
                NOTIFICATION_ID,
                EVENT_ID,
                new EventType("PatientCreated"),
                PSYCHOLOGIST_ID,
                new RecipientAddress("patient@example.com"),
                new NotificationSubject("Patient profile created"),
                new NotificationMessage("A ClinicFlow event was processed"),
                Instant.parse("2026-06-01T10:00:00Z")
        );
    }
}
