package com.clinicflow.auth.adapters.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
class JpaUserEntity {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private com.clinicflow.auth.domain.Role role;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected JpaUserEntity() {
    }

    JpaUserEntity(UUID id, String email, String passwordHash, com.clinicflow.auth.domain.Role role, String fullName, boolean enabled, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.fullName = fullName;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }

    UUID id() {
        return id;
    }

    String email() {
        return email;
    }

    String passwordHash() {
        return passwordHash;
    }

    com.clinicflow.auth.domain.Role role() {
        return role;
    }

    String fullName() {
        return fullName;
    }

    boolean enabled() {
        return enabled;
    }

    Instant createdAt() {
        return createdAt;
    }
}
