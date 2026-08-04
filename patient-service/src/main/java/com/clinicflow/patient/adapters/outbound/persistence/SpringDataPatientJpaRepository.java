package com.clinicflow.patient.adapters.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataPatientJpaRepository extends JpaRepository<JpaPatientEntity, UUID> {
    List<JpaPatientEntity> findByPsychologistId(UUID psychologistId);
}
