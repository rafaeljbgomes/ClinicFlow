package com.clinicflow.patient.adapters.inbound.rest;

import com.clinicflow.patient.adapters.inbound.rest.dto.ChangePatientStatusRequest;
import com.clinicflow.patient.adapters.inbound.rest.dto.CreatePatientRequest;
import com.clinicflow.patient.adapters.inbound.rest.dto.PatientResponse;
import com.clinicflow.patient.adapters.inbound.rest.dto.UpdatePatientRequest;
import com.clinicflow.patient.application.commands.ChangePatientStatusCommand;
import com.clinicflow.patient.application.commands.CreatePatientCommand;
import com.clinicflow.patient.application.commands.UpdatePatientCommand;
import com.clinicflow.patient.application.mapping.PatientApplicationMapper;
import com.clinicflow.patient.application.queries.GetPatientQuery;
import com.clinicflow.patient.application.queries.ListPatientsQuery;
import com.clinicflow.patient.application.results.PatientResult;
import com.clinicflow.patient.domain.BirthDate;
import com.clinicflow.patient.domain.ConsentStatus;
import com.clinicflow.patient.domain.ContactPreference;
import com.clinicflow.patient.domain.EmailAddress;
import com.clinicflow.patient.domain.EmergencyContact;
import com.clinicflow.patient.domain.FullName;
import com.clinicflow.patient.domain.Patient;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PatientStatus;
import com.clinicflow.patient.domain.PhoneNumber;
import com.clinicflow.patient.domain.PsychologistId;
import com.clinicflow.patient.domain.PreferredName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class PatientMappingTest {
    private static final UUID PSYCHOLOGIST_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PATIENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant NOW = Instant.parse("2026-05-26T10:00:00Z");

    private final PatientRestMapper restMapper = Mappers.getMapper(PatientRestMapper.class);
    private final PatientApplicationMapper applicationMapper = Mappers.getMapper(PatientApplicationMapper.class);

    @Test
    void createRequestMapsToNormalizedCommand() {
        // Arrange
        CreatePatientRequest request = new CreatePatientRequest(
                " PATIENT@Example.com ", "  Patient Name ", "Pat",
                LocalDate.of(1990, 1, 1), "+351 912 345 678",
                "Emergency One", "+351 923 456 789", "Partner",
                ContactPreference.EMAIL, ConsentStatus.GRANTED);

        // Act
        CreatePatientCommand command = restMapper.toCommand(PSYCHOLOGIST_ID, request);

        // Assert
        assertThat(command.psychologistId()).isEqualTo(new PsychologistId(PSYCHOLOGIST_ID));
        assertThat(command.email()).isEqualTo(new EmailAddress("patient@example.com"));
        assertThat(command.fullName()).isEqualTo(new FullName("Patient Name"));
        assertThat(command.preferredName()).isEqualTo(new PreferredName("Pat"));
        assertThat(command.birthDate()).isEqualTo(new BirthDate(LocalDate.of(1990, 1, 1)));
        assertThat(command.phone().value()).isEqualTo("+351912345678");
        assertThat(command.emergencyContact())
                .isEqualTo(new EmergencyContact("Emergency One", new PhoneNumber("+351923456789"), "Partner"));
        assertThat(command.contactPreference()).isEqualTo(ContactPreference.EMAIL);
    }

    @Test
    void updateRequestMapsToCommandForTheTargetPatient() {
        // Arrange
        UpdatePatientRequest request = new UpdatePatientRequest("Updated Name", null, null, null, null,
                null, null, ContactPreference.PHONE, ConsentStatus.REVOKED);

        // Act
        UpdatePatientCommand command = restMapper.toCommand(PSYCHOLOGIST_ID, PATIENT_ID, request, 4);

        // Assert
        assertThat(command.patientId()).isEqualTo(new PatientId(PATIENT_ID));
        assertThat(command.phone()).isNull();
        assertThat(command.expectedVersion()).isEqualTo(4);
    }

    @Test
    void statusRequestMapsToCommand() {
        // Arrange
        ChangePatientStatusRequest request = new ChangePatientStatusRequest(PatientStatus.INACTIVE);

        // Act
        ChangePatientStatusCommand command = restMapper.toCommand(PSYCHOLOGIST_ID, PATIENT_ID, request, 4);

        // Assert
        assertThat(command.status()).isEqualTo(PatientStatus.INACTIVE);
        assertThat(command.expectedVersion()).isEqualTo(4);
    }

    @Test
    void getQueryTargetsTheRequestedPatient() {
        // Act
        GetPatientQuery query = restMapper.toGetQuery(PSYCHOLOGIST_ID, PATIENT_ID);

        // Assert
        assertThat(query.patientId()).isEqualTo(new PatientId(PATIENT_ID));
    }

    @Test
    void listQueryTargetsTheAuthenticatedPsychologist() {
        // Act
        ListPatientsQuery query = restMapper.toListQuery(PSYCHOLOGIST_ID);

        // Assert
        assertThat(query.psychologistId()).isEqualTo(new PsychologistId(PSYCHOLOGIST_ID));
    }

    @Test
    void patientMapsToApplicationResult() {
        // Arrange
        Patient patient = storedPatient();

        // Act
        PatientResult result = applicationMapper.toResult(patient);

        // Assert
        assertThat(result.id()).isEqualTo(PATIENT_ID);
        assertThat(result.phone()).isEqualTo("+351912345678");
        assertThat(result.preferredName()).isEqualTo("Pat");
        assertThat(result.birthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(result.status()).isEqualTo(PatientStatus.ACTIVE);
        assertThat(result.version()).isEqualTo(2);
    }

    @Test
    void unsavedPatientHasNoResult() {
        // Arrange
        Patient patient = Patient.create(new PatientId(PATIENT_ID), new PsychologistId(PSYCHOLOGIST_ID),
                new EmailAddress("patient@example.com"), new FullName("Patient Name"), null, null, null, null,
                ContactPreference.EMAIL, ConsentStatus.GRANTED, NOW);

        // Act
        Throwable thrown = catchThrowable(() -> applicationMapper.toResult(patient));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void applicationResultMapsToResponse() {
        // Arrange
        PatientResult result = new PatientResult(PATIENT_ID, PSYCHOLOGIST_ID, "patient@example.com",
                "Patient Name", "+351912345678", "Pat", LocalDate.of(1990, 1, 1), "Emergency One",
                "+351923456789", "Partner", ContactPreference.EMAIL, ConsentStatus.GRANTED,
                NOW, PatientStatus.ACTIVE, NOW, NOW, 2);

        // Act
        PatientResponse response = restMapper.toResponse(result);

        // Assert
        assertThat(response.id()).isEqualTo(PATIENT_ID);
        assertThat(response.phone()).isEqualTo("+351912345678");
        assertThat(response.preferredName()).isEqualTo("Pat");
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(response.status()).isEqualTo(PatientStatus.ACTIVE);
        assertThat(response.version()).isEqualTo(2);
    }

    private static Patient storedPatient() {
        return Patient.rehydrate(
                new PatientId(PATIENT_ID),
                new PsychologistId(PSYCHOLOGIST_ID),
                new EmailAddress("patient@example.com"),
                new FullName("Patient Name"),
                new PreferredName("Pat"),
                new BirthDate(LocalDate.of(1990, 1, 1)),
                new PhoneNumber("+351912345678"),
                new EmergencyContact("Emergency One", new PhoneNumber("+351923456789"), "Partner"),
                ContactPreference.EMAIL,
                ConsentStatus.GRANTED,
                NOW,
                PatientStatus.ACTIVE,
                NOW,
                NOW,
                2L
        );
    }
}
