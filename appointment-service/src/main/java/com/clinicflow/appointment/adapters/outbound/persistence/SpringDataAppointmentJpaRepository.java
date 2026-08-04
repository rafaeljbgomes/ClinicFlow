package com.clinicflow.appointment.adapters.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataAppointmentJpaRepository extends JpaRepository<JpaAppointmentEntity, UUID> {
    List<JpaAppointmentEntity> findByPsychologistId(UUID psychologistId);
    List<JpaAppointmentEntity> findByPatientId(UUID patientId);
}
