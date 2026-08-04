package com.clinicflow.notification.adapters.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

interface SpringDataNotificationJpaRepository extends JpaRepository<JpaNotificationEntity, UUID> {
    boolean existsByEventId(UUID eventId);
    List<JpaNotificationEntity> findByPsychologistIdOrderByCreatedAtDesc(UUID psychologistId);
    Optional<JpaNotificationEntity> findByIdAndPsychologistId(UUID id, UUID psychologistId);
}
