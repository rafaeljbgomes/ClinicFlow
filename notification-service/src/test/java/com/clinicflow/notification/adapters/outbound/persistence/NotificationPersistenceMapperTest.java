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
        Notification notification = notification();

        Notification restored = mapper.toDomain(mapper.toJpa(notification));

        assertThat(restored).isEqualTo(notification);
        assertThat(restored.subject()).isEqualTo(notification.subject());
    }

    @Test
    void repositorySaveKeepsTheSuppliedDomainInstance() {
        SpringDataNotificationJpaRepository delegate = mock(SpringDataNotificationJpaRepository.class);
        NotificationPersistenceMapper mockedMapper = mock(NotificationPersistenceMapper.class);
        Notification notification = notification();
        JpaNotificationEntity jpa = mapper.toJpa(notification);
        when(mockedMapper.toJpa(notification)).thenReturn(jpa);

        new JpaNotificationRepository(delegate, mockedMapper).save(notification);

        verify(delegate).save(jpa);
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
