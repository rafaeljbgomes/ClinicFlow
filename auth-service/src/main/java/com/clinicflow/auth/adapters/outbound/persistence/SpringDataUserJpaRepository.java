package com.clinicflow.auth.adapters.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataUserJpaRepository extends JpaRepository<JpaUserEntity, UUID> {
    boolean existsByEmail(String email);

    Optional<JpaUserEntity> findByEmail(String email);
}
