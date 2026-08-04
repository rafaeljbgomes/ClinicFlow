package com.clinicflow.clinical.adapters.outbound.persistence;

import com.clinicflow.clinical.domain.ClinicalCaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataClinicalCaseJpaRepository extends JpaRepository<JpaClinicalCaseEntity, UUID> {
    List<JpaClinicalCaseEntity> findByPsychologistId(UUID psychologistId);
    List<JpaClinicalCaseEntity> findByPsychologistIdAndPatientId(UUID psychologistId, UUID patientId);
    List<JpaClinicalCaseEntity> findByPsychologistIdAndStatus(UUID psychologistId, ClinicalCaseStatus status);
    Optional<JpaClinicalCaseEntity> findFirstByPsychologistIdAndPatientIdAndStatusNot(
            UUID psychologistId, UUID patientId, ClinicalCaseStatus status);
    boolean existsByPsychologistIdAndPatientIdAndStatusNot(
            UUID psychologistId, UUID patientId, ClinicalCaseStatus status);
}
