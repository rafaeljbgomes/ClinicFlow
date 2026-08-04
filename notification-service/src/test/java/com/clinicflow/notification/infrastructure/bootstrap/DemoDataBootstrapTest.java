package com.clinicflow.notification.infrastructure.bootstrap;

import com.clinicflow.notification.application.ports.NotificationRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

class DemoDataBootstrapTest {
    @Test
    void canBeConstructedBySpring() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(demoBootstrapProperty());
            context.registerBean(NotificationRepository.class, InMemoryNotificationRepository::new);
            context.registerBean(DemoDataBootstrap.class);

            context.refresh();

            assertThat(context.getBean(DemoDataBootstrap.class)).isNotNull();
        }
    }

    private static MapPropertySource demoBootstrapProperty() {
        return new MapPropertySource("test", Map.of("clinicflow.demo.bootstrap.enabled", "true"));
    }

    @Test
    void seedsDemoNotificationsIdempotently() throws Exception {
        InMemoryNotificationRepository notifications = new InMemoryNotificationRepository();
        DemoDataBootstrap bootstrap = new DemoDataBootstrap(
                notifications,
                Clock.fixed(Instant.parse("2026-07-05T10:00:00Z"), ZoneOffset.UTC)
        );

        bootstrap.run(null);
        bootstrap.run(null);

        assertThat(notifications.records()).hasSize(5);
        assertThat(notifications.records().values()).extracting(Notification::status)
                .contains(NotificationStatus.SENT, NotificationStatus.PENDING, NotificationStatus.FAILED);
        assertThat(notifications.records().values()).extracting(Notification::type)
                .contains(NotificationType.EMAIL, NotificationType.SYSTEM);
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
        public List<Notification> findByPsychologistId(PsychologistId psychologistId) {
            return records.values().stream()
                    .filter(notification -> notification.psychologistId()
                            .filter(psychologistId::equals).isPresent())
                    .toList();
        }

        @Override
        public List<Notification> findAll() {
            return List.copyOf(records.values());
        }

        Map<NotificationId, Notification> records() {
            return records;
        }
    }
}
