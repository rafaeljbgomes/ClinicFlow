package com.clinicflow.notification.infrastructure.bootstrap;

import com.clinicflow.notification.application.ports.NotificationRepository;
import com.clinicflow.notification.application.queries.PageQuery;
import com.clinicflow.notification.application.results.PageResult;
import com.clinicflow.notification.domain.EventId;
import com.clinicflow.notification.domain.Notification;
import com.clinicflow.notification.domain.NotificationId;
import com.clinicflow.notification.domain.NotificationStatus;
import com.clinicflow.notification.domain.NotificationType;
import com.clinicflow.notification.domain.PsychologistId;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DemoDataBootstrapTest {
    @Test
    void canBeConstructedBySpring() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            // Arrange
            context.getEnvironment().getPropertySources().addFirst(demoBootstrapProperty());
            context.registerBean(NotificationRepository.class, InMemoryNotificationRepository::new);
            context.registerBean(DemoDataBootstrap.class);

            // Act
            context.refresh();

            // Assert
            assertThat(context.getBean(DemoDataBootstrap.class)).isNotNull();
        }
    }

    @Test
    void seedsNotificationsInEveryDeliveryStatus() throws Exception {
        // Arrange
        InMemoryNotificationRepository notifications = new InMemoryNotificationRepository();
        DemoDataBootstrap bootstrap = bootstrap(notifications);

        // Act
        bootstrap.run(null);

        // Assert
        assertThat(notifications.records().values()).extracting(Notification::status)
                .contains(NotificationStatus.SENT, NotificationStatus.PENDING, NotificationStatus.FAILED);
    }

    @Test
    void seedsNotificationsOfEveryType() throws Exception {
        // Arrange
        InMemoryNotificationRepository notifications = new InMemoryNotificationRepository();
        DemoDataBootstrap bootstrap = bootstrap(notifications);

        // Act
        bootstrap.run(null);

        // Assert
        assertThat(notifications.records().values()).extracting(Notification::type)
                .contains(NotificationType.EMAIL, NotificationType.SYSTEM);
    }

    @Test
    void repeatedRunDoesNotDuplicateDemoNotifications() throws Exception {
        // Arrange
        InMemoryNotificationRepository notifications = new InMemoryNotificationRepository();
        DemoDataBootstrap bootstrap = bootstrap(notifications);
        bootstrap.run(null);

        // Act
        bootstrap.run(null);

        // Assert
        assertThat(notifications.records()).hasSize(5);
    }

    private static DemoDataBootstrap bootstrap(InMemoryNotificationRepository notifications) {
        return new DemoDataBootstrap(
                notifications,
                Clock.fixed(Instant.parse("2026-07-05T10:00:00Z"), ZoneOffset.UTC)
        );
    }

    private static MapPropertySource demoBootstrapProperty() {
        return new MapPropertySource("test", Map.of("clinicflow.demo.bootstrap.enabled", "true"));
    }

    private static final class InMemoryNotificationRepository implements NotificationRepository {
        private final Map<NotificationId, Notification> records = new LinkedHashMap<>();

        @Override
        public void save(Notification notification) {
            records.put(notification.id(), notification);
        }

        @Override
        public boolean existsByEventId(EventId eventId) {
            return records.values().stream().anyMatch(notification -> notification.eventId().equals(eventId));
        }

        @Override
        public Optional<Notification> findById(NotificationId id) {
            return Optional.ofNullable(records.get(id));
        }

        @Override
        public Optional<Notification> findByIdAndPsychologistId(NotificationId id, PsychologistId psychologistId) {
            return findById(id).filter(notification -> notification.psychologistId()
                    .filter(psychologistId::equals).isPresent());
        }

        @Override
        public PageResult<Notification> findPageByPsychologistId(PsychologistId psychologistId,
                                                                 Set<NotificationStatus> statuses, PageQuery page) {
            throw new UnsupportedOperationException("The bootstrap never lists notifications");
        }

        @Override
        public PageResult<Notification> findPage(Set<NotificationStatus> statuses, PageQuery page) {
            throw new UnsupportedOperationException("The bootstrap never lists notifications");
        }

        Map<NotificationId, Notification> records() {
            return records;
        }
    }
}
