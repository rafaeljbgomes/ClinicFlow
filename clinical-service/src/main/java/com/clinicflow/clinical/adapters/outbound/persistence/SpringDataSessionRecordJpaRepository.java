package com.clinicflow.clinical.adapters.outbound.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataSessionRecordJpaRepository extends JpaRepository<JpaSessionRecordEntity, UUID> {
    Optional<JpaSessionRecordEntity> findByAppointmentId(UUID appointmentId);
    Page<JpaSessionRecordEntity> findByClinicalCaseId(UUID clinicalCaseId, Pageable pageable);
    List<JpaSessionRecordEntity> findByPsychologistIdAndPatientId(UUID psychologistId, UUID patientId);
}
