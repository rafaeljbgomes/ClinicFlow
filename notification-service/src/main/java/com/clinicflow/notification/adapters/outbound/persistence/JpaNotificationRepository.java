package com.clinicflow.notification.adapters.outbound.persistence;

import com.clinicflow.notification.application.ports.NotificationRepository;
import com.clinicflow.notification.domain.EventId;
import com.clinicflow.notification.domain.Notification;
import com.clinicflow.notification.domain.NotificationId;
import com.clinicflow.notification.domain.PsychologistId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaNotificationRepository implements NotificationRepository {
    private final SpringDataNotificationJpaRepository delegate;
    private final NotificationPersistenceMapper mapper;

    public JpaNotificationRepository(SpringDataNotificationJpaRepository delegate,
                                     NotificationPersistenceMapper mapper) {
        this.delegate = delegate;
        this.mapper = mapper;
    }

    @Override public void save(Notification notification) { delegate.save(mapper.toJpa(notification)); }
    @Override public boolean existsByEventId(EventId eventId) {
        return delegate.existsByEventId(eventId.value());
    }
    @Override public Optional<Notification> findById(NotificationId id) {
        return delegate.findById(id.value()).map(mapper::toDomain);
    }
    @Override public Optional<Notification> findByIdAndPsychologistId(NotificationId id,
                                                                      PsychologistId psychologistId) {
        return delegate.findByIdAndPsychologistId(id.value(), psychologistId.value()).map(mapper::toDomain);
    }
    @Override public List<Notification> findAll() {
        return delegate.findAll().stream().map(mapper::toDomain).toList();
    }
    @Override public List<Notification> findByPsychologistId(PsychologistId psychologistId) {
        return delegate.findByPsychologistIdOrderByCreatedAtDesc(psychologistId.value()).stream()
                .map(mapper::toDomain).toList();
    }
}
