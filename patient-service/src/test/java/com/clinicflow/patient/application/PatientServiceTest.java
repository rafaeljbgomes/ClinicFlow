package com.clinicflow.patient.application;

import com.clinicflow.patient.application.commands.ChangePatientStatusCommand;
import com.clinicflow.patient.application.commands.CreatePatientCommand;
import com.clinicflow.patient.application.commands.UpdatePatientCommand;
import com.clinicflow.patient.application.exceptions.PatientAccessDeniedException;
import com.clinicflow.patient.application.exceptions.PatientNotFoundException;
import com.clinicflow.patient.application.mapping.PatientApplicationMapper;
import com.clinicflow.patient.application.ports.PatientEventPublisher;
import com.clinicflow.patient.application.ports.PatientRepository;
import com.clinicflow.patient.application.queries.GetPatientQuery;
import com.clinicflow.patient.application.queries.ListPatientsQuery;
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
import com.clinicflow.patient.domain.PreferredName;
import com.clinicflow.patient.domain.PsychologistId;
import com.clinicflow.patient.domain.events.PatientCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {
    private static final PsychologistId OWNER =
            new PsychologistId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private static final PsychologistId OTHER =
            new PsychologistId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    private static final PatientId PATIENT_ID =
            new PatientId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));

    @Mock
    private PatientRepository repository;
    @Mock
    private PatientEventPublisher publisher;

    private PatientService service;

    @BeforeEach
    void setUp() {
        service = new PatientService(repository, publisher, Mappers.getMapper(PatientApplicationMapper.class));
    }

    @Test
    void createSavesPatientBeforePublishingEvent() {
        // Arrange
        CreatePatientCommand command = completeCreateCommand();

        // Act
        service.create(command);

        // Assert
        InOrder order = inOrder(repository, publisher);
        order.verify(repository).save(any(Patient.class));
        order.verify(publisher).publish(any(PatientCreatedEvent.class));
    }

    @Test
    void createPublishesEventForTheSavedPatient() {
        // Arrange
        CreatePatientCommand command = completeCreateCommand();

        // Act
        service.create(command);

        // Assert
        PatientCreatedEvent event = publishedEvent();
        assertThat(event.patientId()).isEqualTo(savedPatient().id());
        assertThat(event.psychologistId()).isEqualTo(OWNER);
    }

    @Test
    void createReturnsActiveResultForTheSavedPatient() {
        // Arrange
        CreatePatientCommand command = completeCreateCommand();

        // Act
        var result = service.create(command);

        // Assert
        assertThat(result.id()).isEqualTo(savedPatient().id().value());
        assertThat(result.status()).isEqualTo(PatientStatus.ACTIVE);
    }

    @Test
    void defaultsContactPreferenceOnCreateWhenMissing() {
        // Arrange
        CreatePatientCommand command = new CreatePatientCommand(
                OWNER,
                new EmailAddress("patient@example.com"),
                new FullName("Patient Name"),
                null,
                null,
                null,
                null,
                null,
                ConsentStatus.GRANTED
        );

        // Act
        var result = service.create(command);

        // Assert
        assertThat(result.contactPreference()).isEqualTo(ContactPreference.EMAIL);
    }

    @Test
    void listsOnlyRepositoryResultsForPsychologist() {
        // Arrange
        when(repository.findByPsychologistId(OWNER)).thenReturn(List.of(patient()));

        // Act
        var results = service.list(new ListPatientsQuery(OWNER));

        // Assert
        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.id()).isEqualTo(PATIENT_ID.value());
            assertThat(result.psychologistId()).isEqualTo(OWNER.value());
        });
    }

    @Test
    void getsOwnedPatient() {
        // Arrange
        when(repository.findById(PATIENT_ID)).thenReturn(Optional.of(patient()));

        // Act
        var result = service.get(new GetPatientQuery(OWNER, PATIENT_ID));

        // Assert
        assertThat(result.id()).isEqualTo(PATIENT_ID.value());
    }

    @Test
    void updatePersistsTheLoadedEntity() {
        // Arrange
        Patient patient = patient();
        when(repository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));

        // Act
        service.update(phoneContactUpdate());

        // Assert
        verify(repository).save(patient);
    }

    @Test
    void updateReturnsTheChangedProfile() {
        // Arrange
        when(repository.findById(PATIENT_ID)).thenReturn(Optional.of(patient()));

        // Act
        var result = service.update(phoneContactUpdate());

        // Assert
        assertThat(result.fullName()).isEqualTo("Updated Name");
        assertThat(result.phone()).isNull();
        assertThat(result.contactPreference()).isEqualTo(ContactPreference.PHONE);
        assertThat(result.consentStatus()).isEqualTo(ConsentStatus.REVOKED);
    }

    @Test
    void keepsCurrentContactPreferenceWhenUpdateOmitsIt() {
        // Arrange
        when(repository.findById(PATIENT_ID)).thenReturn(Optional.of(patient()));
        UpdatePatientCommand command = new UpdatePatientCommand(
                OWNER,
                PATIENT_ID,
                new FullName("Updated Name"),
                null,
                null,
                null,
                null,
                null,
                ConsentStatus.GRANTED
        );

        // Act
        var result = service.update(command);

        // Assert
        assertThat(result.contactPreference()).isEqualTo(ContactPreference.EMAIL);
    }

    @Test
    void changeStatusPersistsTheLoadedEntity() {
        // Arrange
        Patient patient = patient();
        when(repository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));

        // Act
        service.changeStatus(new ChangePatientStatusCommand(OWNER, PATIENT_ID, PatientStatus.INACTIVE));

        // Assert
        verify(repository).save(patient);
    }

    @Test
    void changeStatusReturnsTheNewStatus() {
        // Arrange
        when(repository.findById(PATIENT_ID)).thenReturn(Optional.of(patient()));

        // Act
        var result = service.changeStatus(
                new ChangePatientStatusCommand(OWNER, PATIENT_ID, PatientStatus.INACTIVE));

        // Assert
        assertThat(result.status()).isEqualTo(PatientStatus.INACTIVE);
    }

    @Test
    void getRejectsMissingPatient() {
        // Arrange
        when(repository.findById(PATIENT_ID)).thenReturn(Optional.empty());

        // Act
        Throwable thrown = catchThrowable(() -> service.get(new GetPatientQuery(OWNER, PATIENT_ID)));

        // Assert
        assertThat(thrown).isInstanceOf(PatientNotFoundException.class);
    }

    @Test
    void missingPatientIsNeverSaved() {
        // Arrange
        when(repository.findById(PATIENT_ID)).thenReturn(Optional.empty());

        // Act
        catchThrowable(() -> service.get(new GetPatientQuery(OWNER, PATIENT_ID)));

        // Assert
        verify(repository, never()).save(any());
    }

    @Test
    void changeStatusRejectsPatientOwnedByAnotherPsychologist() {
        // Arrange
        when(repository.findById(PATIENT_ID)).thenReturn(Optional.of(patient()));

        // Act
        Throwable thrown = catchThrowable(() -> service.changeStatus(
                new ChangePatientStatusCommand(OTHER, PATIENT_ID, PatientStatus.INACTIVE)));

        // Assert
        assertThat(thrown).isInstanceOf(PatientAccessDeniedException.class);
    }

    @Test
    void foreignPatientStatusChangeIsNeverSaved() {
        // Arrange
        when(repository.findById(PATIENT_ID)).thenReturn(Optional.of(patient()));

        // Act
        catchThrowable(() -> service.changeStatus(
                new ChangePatientStatusCommand(OTHER, PATIENT_ID, PatientStatus.INACTIVE)));

        // Assert
        verify(repository, never()).save(any());
    }

    private Patient savedPatient() {
        ArgumentCaptor<Patient> captor = ArgumentCaptor.forClass(Patient.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private PatientCreatedEvent publishedEvent() {
        ArgumentCaptor<PatientCreatedEvent> captor = ArgumentCaptor.forClass(PatientCreatedEvent.class);
        verify(publisher).publish(captor.capture());
        return captor.getValue();
    }

    private CreatePatientCommand completeCreateCommand() {
        return new CreatePatientCommand(
                OWNER,
                new EmailAddress("patient@example.com"),
                new FullName("Patient Name"),
                new PreferredName("Pat"),
                new BirthDate(LocalDate.of(1990, 1, 1)),
                new PhoneNumber("+351912345678"),
                new EmergencyContact("Emergency One", new PhoneNumber("+351923456789"), "Partner"),
                ContactPreference.EMAIL,
                ConsentStatus.GRANTED
        );
    }

    private UpdatePatientCommand phoneContactUpdate() {
        return new UpdatePatientCommand(
                OWNER,
                PATIENT_ID,
                new FullName("Updated Name"),
                null,
                null,
                null,
                null,
                ContactPreference.PHONE,
                ConsentStatus.REVOKED
        );
    }

    private Patient patient() {
        Instant now = Instant.parse("2026-06-01T10:00:00Z");
        return Patient.create(PATIENT_ID, OWNER, new EmailAddress("patient@example.com"),
                new FullName("Patient Name"), new PreferredName("Pat"), new BirthDate(LocalDate.of(1990, 1, 1)),
                new PhoneNumber("+351912345678"),
                new EmergencyContact("Emergency One", new PhoneNumber("+351923456789"), "Partner"),
                ContactPreference.EMAIL, ConsentStatus.GRANTED, now);
    }
}
