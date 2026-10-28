package com.clinicflow.appointment.adapters.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

interface SpringDataAppointmentJpaRepository extends JpaRepository<JpaAppointmentEntity, UUID>,
        JpaSpecificationExecutor<JpaAppointmentEntity> {
    List<JpaAppointmentEntity> findByPatientId(UUID patientId);
}
