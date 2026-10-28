package com.clinicflow.notification.adapters.outbound.persistence;

import com.clinicflow.notification.application.queries.PageQuery;
import com.clinicflow.notification.application.results.PageResult;
import com.clinicflow.notification.domain.EventId;
import com.clinicflow.notification.domain.EventType;
import com.clinicflow.notification.domain.FailureReason;
import com.clinicflow.notification.domain.Notification;
import com.clinicflow.notification.domain.NotificationId;
import com.clinicflow.notification.domain.NotificationMessage;
import com.clinicflow.notification.domain.NotificationStatus;
import com.clinicflow.notification.domain.NotificationSubject;
import com.clinicflow.notification.domain.PsychologistId;
import com.clinicflow.notification.domain.RecipientAddress;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
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
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    private static final UUID OWNER = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

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
    void findPageReturnsNewestFirstWithTotals() {
        // Arrange
        Notification oldest = persisted(notification(UUID.randomUUID(), UUID.randomUUID(), OWNER, at(1)));
        Notification middle = persisted(notification(UUID.randomUUID(), UUID.randomUUID(), OWNER, at(2)));
        Notification newest = persisted(notification(UUID.randomUUID(), UUID.randomUUID(), OWNER, at(3)));

        // Act
        PageResult<Notification> first = repository.findPage(Set.of(), new PageQuery(0, 2));
        PageResult<Notification> second = repository.findPage(Set.of(), new PageQuery(1, 2));

        // Assert
        assertThat(first.items()).containsExactly(newest, middle);
        assertThat(second.items()).containsExactly(oldest);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
    }

    @Test
    void findPageBreaksTimestampTiesByIdSoPagesNeverOverlap() {
        // Arrange
        UUID lowerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higherId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        persisted(notification(lowerId, UUID.randomUUID(), OWNER, at(1)));
        persisted(notification(higherId, UUID.randomUUID(), OWNER, at(1)));

        // Act
        PageResult<Notification> first = repository.findPage(Set.of(), new PageQuery(0, 1));
        PageResult<Notification> second = repository.findPage(Set.of(), new PageQuery(1, 1));

        // Assert
        assertThat(first.items()).extracting(n -> n.id().value()).containsExactly(higherId);
        assertThat(second.items()).extracting(n -> n.id().value()).containsExactly(lowerId);
    }

    @Test
    void findPageByPsychologistIdFiltersOwnerAndStatus() {
        // Arrange
        Notification sent = persisted(notification(UUID.randomUUID(), UUID.randomUUID(), OWNER, at(1)));
        Notification failed = persisted(failed(notification(UUID.randomUUID(), UUID.randomUUID(), OWNER, at(2))));
        persisted(failed(notification(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), at(3))));

        // Act
        PageResult<Notification> owned = repository.findPageByPsychologistId(
                new PsychologistId(OWNER), Set.of(), new PageQuery(0, 20));
        PageResult<Notification> ownedFailures = repository.findPageByPsychologistId(
                new PsychologistId(OWNER), Set.of(NotificationStatus.FAILED), new PageQuery(0, 20));

        // Assert
        assertThat(owned.items()).containsExactly(failed, sent);
        assertThat(ownedFailures.items()).containsExactly(failed);
        assertThat(ownedFailures.totalElements()).isEqualTo(1);
    }

    @Test
    void findPageBeyondTheLastPageIsEmptyButKeepsTotals() {
        // Arrange
        persisted(notification(UUID.randomUUID(), UUID.randomUUID()));

        // Act
        PageResult<Notification> page = repository.findPage(Set.of(), new PageQuery(5, 20));

        // Assert
        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1);
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
        return notification(notificationId, eventId, UUID.randomUUID(), at(0));
    }

    private Notification notification(UUID notificationId, UUID eventId, UUID psychologistId, Instant createdAt) {
        return Notification.sent(new NotificationId(notificationId), new EventId(eventId),
                new EventType("PatientCreated"), new PsychologistId(psychologistId),
                new RecipientAddress("patient@example.com"),
                new NotificationSubject("Patient profile created"),
                new NotificationMessage("A ClinicFlow event was processed"),
                createdAt);
    }

    private Notification failed(Notification notification) {
        return Notification.rehydrate(notification.id(), notification.eventId(), notification.eventType(),
                notification.psychologistId().orElseThrow(), notification.type(), NotificationStatus.FAILED,
                notification.recipient().orElse(null), notification.subject(), notification.message(),
                new FailureReason("Mailbox unavailable"), notification.createdAt(), null);
    }

    private static Instant at(int minutes) {
        return Instant.parse("2026-06-01T10:00:00Z").plusSeconds(60L * minutes);
    }
}
