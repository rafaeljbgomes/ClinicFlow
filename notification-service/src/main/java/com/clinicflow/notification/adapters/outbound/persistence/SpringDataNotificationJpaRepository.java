package com.clinicflow.notification.adapters.outbound.persistence;

import com.clinicflow.notification.domain.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;
import java.util.Optional;

interface SpringDataNotificationJpaRepository extends JpaRepository<JpaNotificationEntity, UUID> {
    boolean existsByEventId(UUID eventId);
    Optional<JpaNotificationEntity> findByIdAndPsychologistId(UUID id, UUID psychologistId);
    Page<JpaNotificationEntity> findByStatusIn(Collection<NotificationStatus> statuses, Pageable pageable);
    Page<JpaNotificationEntity> findByPsychologistIdAndStatusIn(UUID psychologistId,
                                                               Collection<NotificationStatus> statuses,
                                                               Pageable pageable);
}
