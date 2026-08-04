package com.clinicflow.patient.adapters.outbound.persistence;

import com.clinicflow.patient.domain.ConsentStatus;
import com.clinicflow.patient.domain.ContactPreference;
import com.clinicflow.patient.domain.PatientStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "patients")
class JpaPatientEntity {
    @Id
    private UUID id;
    @Column(name = "psychologist_id", nullable = false)
    private UUID psychologistId;
    @Column(nullable = false, length = 320)
    private String email;
    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;
    @Column(name = "preferred_name", length = 120)
    private String preferredName;
    @Column(name = "birth_date")
    private LocalDate birthDate;
    @Column(length = 40)
    private String phone;
    @Column(name = "emergency_contact_name", length = 160)
    private String emergencyContactName;
    @Column(name = "emergency_contact_phone", length = 40)
    private String emergencyContactPhone;
    @Column(name = "emergency_contact_relationship", length = 80)
    private String emergencyContactRelationship;
    @Enumerated(EnumType.STRING)
    @Column(name = "contact_preference", nullable = false, length = 32)
    private ContactPreference contactPreference;
    @Enumerated(EnumType.STRING)
    @Column(name = "consent_status", nullable = false, length = 32)
    private ConsentStatus consentStatus;
    @Column(name = "consent_updated_at", nullable = false)
    private Instant consentUpdatedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PatientStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JpaPatientEntity() {
    }

    JpaPatientEntity(UUID id, UUID psychologistId, String email, String fullName, String preferredName,
                     LocalDate birthDate, String phone, String emergencyContactName, String emergencyContactPhone,
                     String emergencyContactRelationship, ContactPreference contactPreference,
                     ConsentStatus consentStatus, Instant consentUpdatedAt, PatientStatus status,
                     Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.psychologistId = psychologistId;
        this.email = email;
        this.fullName = fullName;
        this.preferredName = preferredName;
        this.birthDate = birthDate;
        this.phone = phone;
        this.emergencyContactName = emergencyContactName;
        this.emergencyContactPhone = emergencyContactPhone;
        this.emergencyContactRelationship = emergencyContactRelationship;
        this.contactPreference = contactPreference;
        this.consentStatus = consentStatus;
        this.consentUpdatedAt = consentUpdatedAt;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID id() { return id; }
    UUID psychologistId() { return psychologistId; }
    String email() { return email; }
    String fullName() { return fullName; }
    String preferredName() { return preferredName; }
    LocalDate birthDate() { return birthDate; }
    String phone() { return phone; }
    String emergencyContactName() { return emergencyContactName; }
    String emergencyContactPhone() { return emergencyContactPhone; }
    String emergencyContactRelationship() { return emergencyContactRelationship; }
    ContactPreference contactPreference() { return contactPreference; }
    ConsentStatus consentStatus() { return consentStatus; }
    Instant consentUpdatedAt() { return consentUpdatedAt; }
    PatientStatus status() { return status; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
}
