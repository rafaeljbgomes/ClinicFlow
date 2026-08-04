package com.clinicflow.patient.adapters.outbound.persistence;

import com.clinicflow.patient.application.mapping.MapperConfiguration;
import com.clinicflow.patient.domain.BirthDate;
import com.clinicflow.patient.domain.EmailAddress;
import com.clinicflow.patient.domain.EmergencyContact;
import com.clinicflow.patient.domain.FullName;
import com.clinicflow.patient.domain.Patient;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PhoneNumber;
import com.clinicflow.patient.domain.PreferredName;
import com.clinicflow.patient.domain.PsychologistId;
import org.mapstruct.Mapper;

@Mapper(config = MapperConfiguration.class)
public abstract class PatientPersistenceMapper {
    public abstract String copy(String value);

    public JpaPatientEntity toJpa(Patient patient) {
        return new JpaPatientEntity(
                patient.id().value(),
                patient.psychologistId().value(),
                patient.email().value(),
                patient.fullName().value(),
                patient.preferredName().map(PreferredName::value).orElse(null),
                patient.birthDate().map(BirthDate::value).orElse(null),
                patient.phone().map(phone -> phone.value()).orElse(null),
                patient.emergencyContact().map(EmergencyContact::name).orElse(null),
                patient.emergencyContact().map(contact -> contact.phone().value()).orElse(null),
                patient.emergencyContact().map(EmergencyContact::relationship).orElse(null),
                patient.contactPreference(),
                patient.consentStatus(),
                patient.consentUpdatedAt(),
                patient.status(),
                patient.createdAt(),
                patient.updatedAt()
        );
    }

    public Patient toDomain(JpaPatientEntity entity) {
        return Patient.rehydrate(
                new PatientId(entity.id()),
                new PsychologistId(entity.psychologistId()),
                new EmailAddress(entity.email()),
                new FullName(entity.fullName()),
                entity.preferredName() == null ? null : new PreferredName(entity.preferredName()),
                entity.birthDate() == null ? null : new BirthDate(entity.birthDate()),
                entity.phone() == null ? null : new PhoneNumber(entity.phone()),
                entity.emergencyContactName() == null ? null : new EmergencyContact(
                        entity.emergencyContactName(), new PhoneNumber(entity.emergencyContactPhone()),
                        entity.emergencyContactRelationship()),
                entity.contactPreference(),
                entity.consentStatus(),
                entity.consentUpdatedAt(),
                entity.status(),
                entity.createdAt(),
                entity.updatedAt()
        );
    }
}
