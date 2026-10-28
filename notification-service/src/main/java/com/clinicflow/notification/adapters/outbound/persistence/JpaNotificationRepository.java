package com.clinicflow.notification.adapters.outbound.persistence;

import com.clinicflow.notification.application.ports.NotificationRepository;
import com.clinicflow.notification.application.queries.PageQuery;
import com.clinicflow.notification.application.results.PageResult;
import com.clinicflow.notification.domain.EventId;
import com.clinicflow.notification.domain.Notification;
import com.clinicflow.notification.domain.NotificationId;
import com.clinicflow.notification.domain.NotificationStatus;
import com.clinicflow.notification.domain.PsychologistId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

@Repository
public class JpaNotificationRepository implements NotificationRepository {
    // The id breaks createdAt ties so rows never repeat or vanish between pages.
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

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
    @Override public PageResult<Notification> findPage(Set<NotificationStatus> statuses, PageQuery page) {
        return toResult(delegate.findByStatusIn(orEvery(statuses), pageable(page)));
    }
    @Override public PageResult<Notification> findPageByPsychologistId(PsychologistId psychologistId,
                                                                       Set<NotificationStatus> statuses,
                                                                       PageQuery page) {
        return toResult(delegate.findByPsychologistIdAndStatusIn(
                psychologistId.value(), orEvery(statuses), pageable(page)));
    }

    // An empty IN list is not portable SQL, so "no filter" is spelled as every status.
    private static Set<NotificationStatus> orEvery(Set<NotificationStatus> statuses) {
        return statuses.isEmpty() ? EnumSet.allOf(NotificationStatus.class) : statuses;
    }

    private static PageRequest pageable(PageQuery page) {
        return PageRequest.of(page.number(), page.size(), NEWEST_FIRST);
    }

    private PageResult<Notification> toResult(Page<JpaNotificationEntity> page) {
        return new PageResult<>(page.getContent().stream().map(mapper::toDomain).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
