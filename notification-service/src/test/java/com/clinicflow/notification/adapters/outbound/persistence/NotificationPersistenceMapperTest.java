package com.clinicflow.notification.adapters.outbound.persistence;

import com.clinicflow.notification.domain.EventId;
import com.clinicflow.notification.domain.EventType;
import com.clinicflow.notification.domain.Notification;
import com.clinicflow.notification.domain.NotificationId;
import com.clinicflow.notification.domain.NotificationMessage;
import com.clinicflow.notification.domain.NotificationSubject;
import com.clinicflow.notification.domain.PsychologistId;
import com.clinicflow.notification.domain.RecipientAddress;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationPersistenceMapperTest {
    private final NotificationPersistenceMapper mapper = Mappers.getMapper(NotificationPersistenceMapper.class);

    @Test
    void roundTripsDomainAndPersistenceModels() {
        // Arrange
        Notification notification = notification();

        // Act
        Notification restored = mapper.toDomain(mapper.toJpa(notification));

        // Assert
        assertThat(restored).isEqualTo(notification);
        assertThat(restored.subject()).isEqualTo(notification.subject());
    }

    @Test
    void repositorySavePersistsTheMappedEntity() {
        // Arrange
        SpringDataNotificationJpaRepository delegate = mock(SpringDataNotificationJpaRepository.class);
        NotificationPersistenceMapper mockedMapper = mock(NotificationPersistenceMapper.class);
        Notification notification = notification();
        JpaNotificationEntity jpa = mapper.toJpa(notification);
        when(mockedMapper.toJpa(notification)).thenReturn(jpa);
        JpaNotificationRepository repository = new JpaNotificationRepository(delegate, mockedMapper);

        // Act
        repository.save(notification);

        // Assert
        verify(delegate).save(jpa);
    }

    @Test
    void repositorySaveKeepsTheSuppliedDomainInstance() {
        // Arrange
        SpringDataNotificationJpaRepository delegate = mock(SpringDataNotificationJpaRepository.class);
        Notification notification = notification();
        JpaNotificationRepository repository = new JpaNotificationRepository(delegate, mapper);

        // Act
        repository.save(notification);

        // Assert
        assertThat(notification.eventType()).isEqualTo(new EventType("PatientCreated"));
    }

    private Notification notification() {
        Instant now = Instant.parse("2026-05-26T10:00:00Z");
        return Notification.sent(new NotificationId(UUID.randomUUID()), new EventId(UUID.randomUUID()),
                new EventType("PatientCreated"), new PsychologistId(UUID.randomUUID()),
                new RecipientAddress("patient@example.com"),
                new NotificationSubject("Subject"), new NotificationMessage("Message"), now);
    }
}
