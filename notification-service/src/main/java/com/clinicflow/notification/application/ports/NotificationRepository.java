package com.clinicflow.notification.application.ports;

import com.clinicflow.notification.application.queries.PageQuery;
import com.clinicflow.notification.application.results.PageResult;
import com.clinicflow.notification.domain.EventId;
import com.clinicflow.notification.domain.Notification;
import com.clinicflow.notification.domain.NotificationId;
import com.clinicflow.notification.domain.NotificationStatus;
import com.clinicflow.notification.domain.PsychologistId;

import java.util.Optional;
import java.util.Set;

public interface NotificationRepository {
    void save(Notification notification);
    boolean existsByEventId(EventId eventId);
    Optional<Notification> findById(NotificationId id);
    Optional<Notification> findByIdAndPsychologistId(NotificationId id, PsychologistId psychologistId);
    // Pages are newest first; an empty status set matches every status.
    PageResult<Notification> findPage(Set<NotificationStatus> statuses, PageQuery page);
    PageResult<Notification> findPageByPsychologistId(PsychologistId psychologistId,
                                                      Set<NotificationStatus> statuses, PageQuery page);
}
