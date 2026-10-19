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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaNotificationRepository.class, NotificationPersistenceMapperImpl.class})
@Testcontainers
class JpaNotificationRepositoryIT {
    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired
    private JpaNotificationRepository repository;
    @Autowired
    private SpringDataNotificationJpaRepository delegate;

    @Test
    void existsByEventIdFindsPersistedNotification() {
        // Arrange
        Notification notification = persisted(notification(UUID.randomUUID(), UUID.randomUUID()));

        // Act
        boolean exists = repository.existsByEventId(notification.eventId());

        // Assert
        assertThat(exists).isTrue();
    }

    @Test
    void findByIdReturnsPersistedNotification() {
        // Arrange
        Notification notification = persisted(notification(UUID.randomUUID(), UUID.randomUUID()));

        // Act
        Optional<Notification> found = repository.findById(notification.id());

        // Assert
        assertThat(found).contains(notification);
    }

    @Test
    void findAllReturnsPersistedNotifications() {
        // Arrange
        Notification notification = persisted(notification(UUID.randomUUID(), UUID.randomUUID()));

        // Act
        List<Notification> found = repository.findAll();

        // Assert
        assertThat(found).containsExactly(notification);
    }

    @Test
    void enforcesEventIdIdempotencyAtDatabaseBoundary() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        persisted(notification(UUID.randomUUID(), eventId));
        repository.save(notification(UUID.randomUUID(), eventId));

        // Act
        Throwable thrown = catchThrowable(delegate::flush);

        // Assert
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Notification persisted(Notification notification) {
        repository.save(notification);
        delegate.flush();
        return notification;
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
