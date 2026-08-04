package com.clinicflow.clinical.adapters.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataPracticeProfileJpaRepository extends JpaRepository<JpaPracticeProfileEntity, UUID> {
}
