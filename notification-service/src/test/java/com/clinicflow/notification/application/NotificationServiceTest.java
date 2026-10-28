package com.clinicflow.notification.application;

import com.clinicflow.notification.application.commands.ProcessNotificationEventCommand;
import com.clinicflow.notification.application.exceptions.NotificationNotFoundException;
import com.clinicflow.notification.application.mapping.NotificationApplicationMapper;
import com.clinicflow.notification.application.ports.NotificationRepository;
import com.clinicflow.notification.application.queries.GetNotificationQuery;
import com.clinicflow.notification.application.queries.ListNotificationsQuery;
import com.clinicflow.notification.application.queries.PageQuery;
import com.clinicflow.notification.application.results.PageResult;
import com.clinicflow.notification.domain.EventId;
import com.clinicflow.notification.domain.EventType;
import com.clinicflow.notification.domain.Notification;
import com.clinicflow.notification.domain.NotificationId;
import com.clinicflow.notification.domain.NotificationMessage;
import com.clinicflow.notification.domain.NotificationStatus;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
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
        // Arrange
        ProcessNotificationEventCommand command = new ProcessNotificationEventCommand(
                EVENT_ID,
                new EventType("PatientCreated"),
                PSYCHOLOGIST_ID,
                new RecipientAddress("patient@example.com")
        );

        // Act
        service.processEvent(command);

        // Assert
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.eventId()).isEqualTo(EVENT_ID);
        assertThat(saved.recipient()).contains(new RecipientAddress("patient@example.com"));
        assertThat(saved.subject().value()).isEqualTo("Patient profile created");
    }

    @Test
    void skipsDuplicateEventId() {
        // Arrange
        when(repository.existsByEventId(EVENT_ID)).thenReturn(true);
        ProcessNotificationEventCommand command = new ProcessNotificationEventCommand(
                EVENT_ID, new EventType("PatientCreated"), PSYCHOLOGIST_ID, null);

        // Act
        service.processEvent(command);

        // Assert
        verify(repository, never()).save(any());
    }

    @Test
    void listReturnsTheRequestedPageOfEveryNotification() {
        // Arrange
        var page = new PageQuery(2, 5);
        when(repository.findPage(Set.of(NotificationStatus.FAILED), page))
                .thenReturn(new PageResult<>(List.of(notification()), 2, 5, 11));

        // Act
        var results = service.list(new ListNotificationsQuery(Set.of(NotificationStatus.FAILED), page));

        // Assert
        assertThat(results.items()).extracting(result -> result.id()).containsExactly(NOTIFICATION_ID.value());
        assertThat(results.totalElements()).isEqualTo(11);
        assertThat(results.totalPages()).isEqualTo(3);
    }

    @Test
    void listForPsychologistReadsOnlyThatPsychologistsPage() {
        // Arrange
        var page = new PageQuery(0, 20);
        when(repository.findPageByPsychologistId(PSYCHOLOGIST_ID, Set.of(), page))
                .thenReturn(new PageResult<>(List.of(notification()), 0, 20, 1));

        // Act
        var results = service.listForPsychologist(new ListNotificationsQuery(null, page), PSYCHOLOGIST_ID);

        // Assert
        assertThat(results.items()).extracting(result -> result.id()).containsExactly(NOTIFICATION_ID.value());
    }

    @Test
    void pageQueryRejectsNegativePagesAndUnboundedSizes() {
        // Act
        Throwable negative = catchThrowable(() -> new PageQuery(-1, 20));
        Throwable empty = catchThrowable(() -> new PageQuery(0, 0));
        Throwable oversized = catchThrowable(() -> new PageQuery(0, PageQuery.MAX_SIZE + 1));

        // Assert
        assertThat(negative).isInstanceOf(IllegalArgumentException.class);
        assertThat(empty).isInstanceOf(IllegalArgumentException.class);
        assertThat(oversized).isInstanceOf(IllegalArgumentException.class);
        assertThat(new PageQuery(0, PageQuery.MAX_SIZE).size()).isEqualTo(PageQuery.MAX_SIZE);
    }

    @Test
    void listQueryRequiresAPage() {
        // Act
        Throwable thrown = catchThrowable(() -> new ListNotificationsQuery(Set.of(), null));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pageResultCountsPartialLastPages() {
        // Assert
        assertThat(new PageResult<>(List.of(), 0, 20, 0).totalPages()).isZero();
        assertThat(new PageResult<>(List.of(), 0, 20, 20).totalPages()).isEqualTo(1);
        assertThat(new PageResult<>(List.of(), 0, 20, 21).totalPages()).isEqualTo(2);
    }

    @Test
    void getReturnsTheRequestedNotification() {
        // Arrange
        when(repository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(notification()));

        // Act
        var result = service.get(new GetNotificationQuery(NOTIFICATION_ID));

        // Assert
        assertThat(result.eventId()).isEqualTo(EVENT_ID.value());
    }

    @Test
    void throwsWhenNotificationDoesNotExist() {
        // Arrange
        when(repository.findById(NOTIFICATION_ID)).thenReturn(Optional.empty());

        // Act
        Throwable thrown = catchThrowable(() -> service.get(new GetNotificationQuery(NOTIFICATION_ID)));

        // Assert
        assertThat(thrown).isInstanceOf(NotificationNotFoundException.class);
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
