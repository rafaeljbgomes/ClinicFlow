package com.clinicflow.clinical.adapters.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataCarePlanJpaRepository extends JpaRepository<JpaCarePlanEntity, UUID> {
    Optional<JpaCarePlanEntity> findByClinicalCaseId(UUID clinicalCaseId);
}
