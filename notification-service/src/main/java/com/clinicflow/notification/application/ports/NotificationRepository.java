package com.clinicflow.notification.application.ports;

import com.clinicflow.notification.domain.EventId;
import com.clinicflow.notification.domain.Notification;
import com.clinicflow.notification.domain.NotificationId;
import com.clinicflow.notification.domain.PsychologistId;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository {
    void save(Notification notification);
    boolean existsByEventId(EventId eventId);
    Optional<Notification> findById(NotificationId id);
    Optional<Notification> findByIdAndPsychologistId(NotificationId id, PsychologistId psychologistId);
    List<Notification> findAll();
    List<Notification> findByPsychologistId(PsychologistId psychologistId);
}
