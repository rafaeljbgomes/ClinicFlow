package com.clinicflow.patient.adapters.inbound.rest;

import com.clinicflow.patient.adapters.inbound.rest.dto.ChangePatientStatusRequest;
import com.clinicflow.patient.adapters.inbound.rest.dto.CreatePatientRequest;
import com.clinicflow.patient.adapters.inbound.rest.dto.PatientResponse;
import com.clinicflow.patient.adapters.inbound.rest.dto.UpdatePatientRequest;
import com.clinicflow.patient.application.commands.ChangePatientStatusCommand;
import com.clinicflow.patient.application.commands.CreatePatientCommand;
import com.clinicflow.patient.application.commands.UpdatePatientCommand;
import com.clinicflow.patient.application.mapping.MapperConfiguration;
import com.clinicflow.patient.application.queries.GetPatientQuery;
import com.clinicflow.patient.application.queries.ListPatientsQuery;
import com.clinicflow.patient.application.results.PatientResult;
import com.clinicflow.patient.domain.BirthDate;
import com.clinicflow.patient.domain.ContactPreference;
import com.clinicflow.patient.domain.EmailAddress;
import com.clinicflow.patient.domain.EmergencyContact;
import com.clinicflow.patient.domain.FullName;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PhoneNumber;
import com.clinicflow.patient.domain.PreferredName;
import com.clinicflow.patient.domain.PsychologistId;
import org.mapstruct.Mapper;

import java.time.LocalDate;
import java.util.UUID;

@Mapper(config = MapperConfiguration.class)
public abstract class PatientRestMapper {
    public CreatePatientCommand toCommand(UUID psychologistId, CreatePatientRequest request) {
        return new CreatePatientCommand(
                new PsychologistId(psychologistId),
                new EmailAddress(request.email()),
                new FullName(request.fullName()),
                preferredName(request.preferredName()),
                birthDate(request.birthDate()),
                phone(request.phone()),
                emergencyContact(request.emergencyContactName(), request.emergencyContactPhone(),
                        request.emergencyContactRelationship()),
                request.contactPreference(),
                request.consentStatus()
        );
    }

    public UpdatePatientCommand toCommand(UUID psychologistId, UUID patientId, UpdatePatientRequest request) {
        return new UpdatePatientCommand(
                new PsychologistId(psychologistId),
                new PatientId(patientId),
                new FullName(request.fullName()),
                preferredName(request.preferredName()),
                birthDate(request.birthDate()),
                phone(request.phone()),
                emergencyContact(request.emergencyContactName(), request.emergencyContactPhone(),
                        request.emergencyContactRelationship()),
                contactPreference(request.contactPreference()),
                request.consentStatus()
        );
    }

    public ChangePatientStatusCommand toCommand(UUID psychologistId, UUID patientId,
                                                ChangePatientStatusRequest request) {
        return new ChangePatientStatusCommand(
                new PsychologistId(psychologistId), new PatientId(patientId), request.status());
    }

    public GetPatientQuery toGetQuery(UUID psychologistId, UUID patientId) {
        return new GetPatientQuery(new PsychologistId(psychologistId), new PatientId(patientId));
    }

    public ListPatientsQuery toListQuery(UUID psychologistId) {
        return new ListPatientsQuery(new PsychologistId(psychologistId));
    }

    public abstract PatientResponse toResponse(PatientResult result);

    private PhoneNumber phone(String value) {
        return value == null || value.isBlank() ? null : new PhoneNumber(value);
    }

    private PreferredName preferredName(String value) {
        return value == null || value.isBlank() ? null : new PreferredName(value);
    }

    private BirthDate birthDate(LocalDate value) {
        return value == null ? null : new BirthDate(value);
    }

    private EmergencyContact emergencyContact(String name, String phone, String relationship) {
        if ((name == null || name.isBlank()) && (phone == null || phone.isBlank())
                && (relationship == null || relationship.isBlank())) {
            return null;
        }
        return new EmergencyContact(name, phone(phone), relationship);
    }

    private ContactPreference contactPreference(ContactPreference value) {
        return value == null ? ContactPreference.EMAIL : value;
    }
}
