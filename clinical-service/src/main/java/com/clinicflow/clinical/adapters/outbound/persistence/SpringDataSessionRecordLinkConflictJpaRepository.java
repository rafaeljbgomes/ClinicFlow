package com.clinicflow.clinical.adapters.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataSessionRecordLinkConflictJpaRepository
        extends JpaRepository<JpaSessionRecordLinkConflictEntity, UUID> {
}
