package com.clinicflow.patient.application.mapping;

import com.clinicflow.patient.application.results.PatientResult;
import com.clinicflow.patient.domain.Patient;
import com.clinicflow.patient.domain.PhoneNumber;

import org.mapstruct.Mapper;

@Mapper(config = MapperConfiguration.class)
public abstract class PatientApplicationMapper {
    public abstract String copy(String value);

    public PatientResult toResult(Patient patient) {
        return new PatientResult(
                patient.id().value(),
                patient.psychologistId().value(),
                patient.email().value(),
                patient.fullName().value(),
                patient.phone().map(PhoneNumber::value).orElse(null),
                patient.preferredName().map(preferredName -> preferredName.value()).orElse(null),
                patient.birthDate().map(birthDate -> birthDate.value()).orElse(null),
                patient.emergencyContact().map(contact -> contact.name()).orElse(null),
                patient.emergencyContact().map(contact -> contact.phone().value()).orElse(null),
                patient.emergencyContact().map(contact -> contact.relationship()).orElse(null),
                patient.contactPreference(),
                patient.consentStatus(),
                patient.consentUpdatedAt(),
                patient.status(),
                patient.createdAt(),
                patient.updatedAt()
        );
    }
}
