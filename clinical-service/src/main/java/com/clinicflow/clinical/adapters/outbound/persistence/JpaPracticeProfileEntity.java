package com.clinicflow.clinical.adapters.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "practice_profiles")
class JpaPracticeProfileEntity {
    @Id
    @Column(name = "psychologist_id")
    private UUID psychologistId;
    @Column(name = "professional_registration", length = 80)
    private String professionalRegistration;
    @Column(nullable = false, length = 80)
    private String timezone;
    @Column(name = "default_session_duration_minutes", nullable = false)
    private int defaultSessionDurationMinutes;
    @Column(name = "primary_location", length = 160)
    private String primaryLocation;
    @Column(name = "telehealth_enabled", nullable = false)
    private boolean telehealthEnabled;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JpaPracticeProfileEntity() {
    }

    JpaPracticeProfileEntity(UUID psychologistId, String professionalRegistration, String timezone,
                             int defaultSessionDurationMinutes, String primaryLocation,
                             boolean telehealthEnabled, Instant createdAt, Instant updatedAt) {
        this.psychologistId = psychologistId;
        this.professionalRegistration = professionalRegistration;
        this.timezone = timezone;
        this.defaultSessionDurationMinutes = defaultSessionDurationMinutes;
        this.primaryLocation = primaryLocation;
        this.telehealthEnabled = telehealthEnabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID psychologistId() { return psychologistId; }
    String professionalRegistration() { return professionalRegistration; }
    String timezone() { return timezone; }
    int defaultSessionDurationMinutes() { return defaultSessionDurationMinutes; }
    String primaryLocation() { return primaryLocation; }
    boolean telehealthEnabled() { return telehealthEnabled; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
}
