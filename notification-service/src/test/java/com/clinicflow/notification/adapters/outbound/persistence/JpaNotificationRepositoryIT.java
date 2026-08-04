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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaNotificationRepository.class, NotificationPersistenceMapperImpl.class})
@Testcontainers
class JpaNotificationRepositoryIT {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JpaNotificationRepository repository;
    @Autowired
    private SpringDataNotificationJpaRepository delegate;

    @Test
    void persistsFindsAndListsNotification() {
        Notification notification = notification(UUID.randomUUID(), UUID.randomUUID());

        repository.save(notification);
        delegate.flush();

        assertThat(repository.existsByEventId(notification.eventId())).isTrue();
        assertThat(repository.findById(notification.id())).contains(notification);
        assertThat(repository.findAll()).containsExactly(notification);
    }

    @Test
    void enforcesEventIdIdempotencyAtDatabaseBoundary() {
        UUID eventId = UUID.randomUUID();
        repository.save(notification(UUID.randomUUID(), eventId));
        delegate.flush();

        repository.save(notification(UUID.randomUUID(), eventId));

        assertThatThrownBy(delegate::flush)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Notification notification(UUID notificationId, UUID eventId) {
        return Notification.sent(new NotificationId(notificationId), new EventId(eventId),
                new EventType("PatientCreated"), new PsychologistId(UUID.randomUUID()),
                new RecipientAddress("patient@example.com"),
                new NotificationSubject("Patient profile created"),
                new NotificationMessage("A ClinicFlow event was processed"),
                Instant.parse("2026-06-01T10:00:00Z"));
    }
}
