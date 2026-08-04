package com.clinicflow.patient.adapters.inbound.rest;

import com.clinicflow.patient.adapters.inbound.rest.dto.ChangePatientStatusRequest;
import com.clinicflow.patient.adapters.inbound.rest.dto.CreatePatientRequest;
import com.clinicflow.patient.adapters.inbound.rest.dto.UpdatePatientRequest;
import com.clinicflow.patient.application.mapping.PatientApplicationMapper;
import com.clinicflow.patient.domain.BirthDate;
import com.clinicflow.patient.domain.ConsentStatus;
import com.clinicflow.patient.domain.ContactPreference;
import com.clinicflow.patient.domain.EmailAddress;
import com.clinicflow.patient.domain.EmergencyContact;
import com.clinicflow.patient.domain.FullName;
import com.clinicflow.patient.domain.Patient;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PatientStatus;
import com.clinicflow.patient.domain.PsychologistId;
import com.clinicflow.patient.domain.PreferredName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PatientMappingTest {
    private final PatientRestMapper restMapper = Mappers.getMapper(PatientRestMapper.class);
    private final PatientApplicationMapper applicationMapper = Mappers.getMapper(PatientApplicationMapper.class);

    @Test
    void mapsRestInputsAndApplicationOutputs() {
        UUID psychologistId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        CreatePatientRequest request = new CreatePatientRequest(
                " PATIENT@Example.com ", "  Patient Name ", "Pat",
                LocalDate.of(1990, 1, 1), "+351 912 345 678",
                "Emergency One", "+351 923 456 789", "Partner",
                ContactPreference.EMAIL, ConsentStatus.GRANTED);

        var createCommand = restMapper.toCommand(psychologistId, request);
        var updateCommand = restMapper.toCommand(
                psychologistId,
                patientId,
                new UpdatePatientRequest("Updated Name", null, null, null, null,
                        null, null, ContactPreference.PHONE, ConsentStatus.REVOKED)
        );
        var statusCommand = restMapper.toCommand(
                psychologistId,
                patientId,
                new ChangePatientStatusRequest(PatientStatus.INACTIVE)
        );

        assertThat(createCommand.psychologistId()).isEqualTo(new PsychologistId(psychologistId));
        assertThat(createCommand.email()).isEqualTo(new EmailAddress("patient@example.com"));
        assertThat(createCommand.fullName()).isEqualTo(new FullName("Patient Name"));
        assertThat(createCommand.preferredName()).isEqualTo(new PreferredName("Pat"));
        assertThat(createCommand.birthDate()).isEqualTo(new BirthDate(LocalDate.of(1990, 1, 1)));
        assertThat(createCommand.phone().value()).isEqualTo("+351912345678");
        assertThat(createCommand.emergencyContact())
                .isEqualTo(new EmergencyContact("Emergency One", new com.clinicflow.patient.domain.PhoneNumber("+351923456789"), "Partner"));
        assertThat(createCommand.contactPreference()).isEqualTo(ContactPreference.EMAIL);
        assertThat(updateCommand.patientId()).isEqualTo(new PatientId(patientId));
        assertThat(updateCommand.phone()).isNull();
        assertThat(statusCommand.status()).isEqualTo(PatientStatus.INACTIVE);
        assertThat(restMapper.toGetQuery(psychologistId, patientId).patientId())
                .isEqualTo(new PatientId(patientId));
        assertThat(restMapper.toListQuery(psychologistId).psychologistId())
                .isEqualTo(new PsychologistId(psychologistId));

        Patient patient = Patient.create(
                new PatientId(patientId),
                createCommand.psychologistId(),
                createCommand.email(),
                createCommand.fullName(),
                createCommand.preferredName(),
                createCommand.birthDate(),
                createCommand.phone(),
                createCommand.emergencyContact(),
                createCommand.contactPreference(),
                createCommand.consentStatus(),
                Instant.parse("2026-05-26T10:00:00Z")
        );

        var response = restMapper.toResponse(applicationMapper.toResult(patient));

        assertThat(response.id()).isEqualTo(patientId);
        assertThat(response.phone()).isEqualTo("+351912345678");
        assertThat(response.preferredName()).isEqualTo("Pat");
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(response.status()).isEqualTo(PatientStatus.ACTIVE);
    }
}
