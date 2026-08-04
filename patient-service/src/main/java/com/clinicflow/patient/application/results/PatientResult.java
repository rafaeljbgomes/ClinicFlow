package com.clinicflow.patient.application.results;

import com.clinicflow.patient.domain.ConsentStatus;
import com.clinicflow.patient.domain.ContactPreference;
import com.clinicflow.patient.domain.PatientStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PatientResult(UUID id, UUID psychologistId, String email, String fullName, String phone,
                            String preferredName, LocalDate birthDate, String emergencyContactName,
                            String emergencyContactPhone, String emergencyContactRelationship,
                            ContactPreference contactPreference, ConsentStatus consentStatus,
                            Instant consentUpdatedAt, PatientStatus status,
                            Instant createdAt, Instant updatedAt) {
}
