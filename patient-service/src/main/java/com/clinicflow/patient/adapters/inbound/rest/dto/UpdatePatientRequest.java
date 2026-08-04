package com.clinicflow.patient.adapters.inbound.rest.dto;

import com.clinicflow.patient.domain.ConsentStatus;
import com.clinicflow.patient.domain.ContactPreference;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdatePatientRequest(
        @NotBlank @Size(max = 160) String fullName,
        @Size(max = 120) String preferredName,
        LocalDate birthDate,
        @Size(max = 40) String phone,
        @Size(max = 160) String emergencyContactName,
        @Size(max = 40) String emergencyContactPhone,
        @Size(max = 80) String emergencyContactRelationship,
        ContactPreference contactPreference,
        @NotNull ConsentStatus consentStatus
) {
}
