package com.clinicflow.patient.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.Objects;

public final class Patient {
    private final PatientId id;
    private final PsychologistId psychologistId;
    private final EmailAddress email;
    private FullName fullName;
    private PreferredName preferredName;
    private BirthDate birthDate;
    private PhoneNumber phone;
    private EmergencyContact emergencyContact;
    private ContactPreference contactPreference;
    private ConsentStatus consentStatus;
    private Instant consentUpdatedAt;
    private PatientStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Patient(PatientId id, PsychologistId psychologistId, EmailAddress email, FullName fullName,
                    PreferredName preferredName, BirthDate birthDate, PhoneNumber phone,
                    EmergencyContact emergencyContact, ContactPreference contactPreference,
                    ConsentStatus consentStatus, Instant consentUpdatedAt, PatientStatus status,
                    Instant createdAt, Instant updatedAt) {
        if (id == null || psychologistId == null || email == null || fullName == null
                || contactPreference == null || consentStatus == null || consentUpdatedAt == null
                || status == null || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("Patient required fields are missing");
        }
        this.id = id;
        this.psychologistId = psychologistId;
        this.email = email;
        this.fullName = fullName;
        this.preferredName = preferredName;
        this.birthDate = birthDate;
        this.phone = phone;
        this.emergencyContact = emergencyContact;
        this.contactPreference = contactPreference;
        this.consentStatus = consentStatus;
        this.consentUpdatedAt = consentUpdatedAt;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Patient create(PatientId id, PsychologistId psychologistId, EmailAddress email,
                                 FullName fullName, PreferredName preferredName, BirthDate birthDate,
                                 PhoneNumber phone, EmergencyContact emergencyContact,
                                 ContactPreference contactPreference, ConsentStatus consentStatus, Instant now) {
        return new Patient(id, psychologistId, email, fullName, preferredName, birthDate, phone,
                emergencyContact, contactPreference, consentStatus, now, PatientStatus.ACTIVE, now, now);
    }

    public static Patient rehydrate(PatientId id, PsychologistId psychologistId, EmailAddress email,
                                    FullName fullName, PreferredName preferredName, BirthDate birthDate,
                                    PhoneNumber phone, EmergencyContact emergencyContact,
                                    ContactPreference contactPreference, ConsentStatus consentStatus,
                                    Instant consentUpdatedAt, PatientStatus status, Instant createdAt,
                                    Instant updatedAt) {
        return new Patient(id, psychologistId, email, fullName, preferredName, birthDate, phone,
                emergencyContact, contactPreference, consentStatus, consentUpdatedAt, status, createdAt, updatedAt);
    }

    public Patient update(FullName newFullName, PreferredName newPreferredName, BirthDate newBirthDate,
                          PhoneNumber newPhone, EmergencyContact newEmergencyContact,
                          ContactPreference newContactPreference, ConsentStatus newConsentStatus, Instant now) {
        requireUpdateTime(now);
        if (newFullName == null || newContactPreference == null || newConsentStatus == null) {
            throw new IllegalArgumentException("Patient update fields are missing");
        }
        this.fullName = newFullName;
        this.preferredName = newPreferredName;
        this.birthDate = newBirthDate;
        this.phone = newPhone;
        this.emergencyContact = newEmergencyContact;
        this.contactPreference = newContactPreference;
        if (this.consentStatus != newConsentStatus) {
            this.consentUpdatedAt = now;
        }
        this.consentStatus = newConsentStatus;
        this.updatedAt = now;
        return this;
    }

    public Patient changeStatus(PatientStatus newStatus, Instant now) {
        requireUpdateTime(now);
        if (newStatus == null) {
            throw new IllegalArgumentException("Patient status is required");
        }
        this.status = newStatus;
        this.updatedAt = now;
        return this;
    }

    private void requireUpdateTime(Instant now) {
        if (now == null || now.isBefore(createdAt)) {
            throw new IllegalArgumentException("Patient update time is invalid");
        }
    }

    public PatientId id() { return new PatientId(id.value()); }
    public PsychologistId psychologistId() { return new PsychologistId(psychologistId.value()); }
    public EmailAddress email() { return new EmailAddress(email.value()); }
    public FullName fullName() { return new FullName(fullName.value()); }
    public Optional<PreferredName> preferredName() {
        return Optional.ofNullable(preferredName).map(value -> new PreferredName(value.value()));
    }
    public Optional<BirthDate> birthDate() {
        return Optional.ofNullable(birthDate).map(value -> new BirthDate(value.value()));
    }
    public Optional<PhoneNumber> phone() {
        return Optional.ofNullable(phone).map(value -> new PhoneNumber(value.value()));
    }
    public Optional<EmergencyContact> emergencyContact() {
        return Optional.ofNullable(emergencyContact)
                .map(value -> new EmergencyContact(value.name(), value.phone(), value.relationship()));
    }
    public ContactPreference contactPreference() { return contactPreference; }
    public ConsentStatus consentStatus() { return consentStatus; }
    public Instant consentUpdatedAt() { return consentUpdatedAt; }
    public PatientStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Patient patient && id.equals(patient.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
