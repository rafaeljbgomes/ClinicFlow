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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void createsPatientThenPublishesEvent() {
        CreatePatientCommand command = new CreatePatientCommand(
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

        var result = service.create(command);

        ArgumentCaptor<Patient> patientCaptor = ArgumentCaptor.forClass(Patient.class);
        ArgumentCaptor<PatientCreatedEvent> eventCaptor = ArgumentCaptor.forClass(PatientCreatedEvent.class);
        InOrder order = inOrder(repository, publisher);
        order.verify(repository).save(patientCaptor.capture());
        order.verify(publisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().patientId()).isEqualTo(patientCaptor.getValue().id());
        assertThat(eventCaptor.getValue().psychologistId()).isEqualTo(OWNER);
        assertThat(result.id()).isEqualTo(patientCaptor.getValue().id().value());
        assertThat(result.status()).isEqualTo(PatientStatus.ACTIVE);
    }

    @Test
    void defaultsContactPreferenceOnCreateWhenMissing() {
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

        var result = service.create(command);

        assertThat(result.contactPreference()).isEqualTo(ContactPreference.EMAIL);
    }

    @Test
    void listsOnlyRepositoryResultsForPsychologist() {
        when(repository.findByPsychologistId(OWNER)).thenReturn(List.of(patient()));

        var results = service.list(new ListPatientsQuery(OWNER));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.id()).isEqualTo(PATIENT_ID.value());
            assertThat(result.psychologistId()).isEqualTo(OWNER.value());
        });
    }

    @Test
    void getsOwnedPatient() {
        when(repository.findById(PATIENT_ID)).thenReturn(Optional.of(patient()));

        var result = service.get(new GetPatientQuery(OWNER, PATIENT_ID));

        assertThat(result.id()).isEqualTo(PATIENT_ID.value());
    }

    @Test
    void updatesOwnedPatientAndPersistsSameEntity() {
        Patient patient = patient();
        when(repository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));

        var result = service.update(new UpdatePatientCommand(
                OWNER,
                PATIENT_ID,
                new FullName("Updated Name"),
                null,
                null,
                null,
                null,
                ContactPreference.PHONE,
                ConsentStatus.REVOKED
        ));

        verify(repository).save(patient);
        assertThat(result.fullName()).isEqualTo("Updated Name");
        assertThat(result.phone()).isNull();
        assertThat(result.contactPreference()).isEqualTo(ContactPreference.PHONE);
        assertThat(result.consentStatus()).isEqualTo(ConsentStatus.REVOKED);
    }

    @Test
    void keepsCurrentContactPreferenceWhenUpdateOmitsIt() {
        Patient patient = patient();
        when(repository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));

        var result = service.update(new UpdatePatientCommand(
                OWNER,
                PATIENT_ID,
                new FullName("Updated Name"),
                null,
                null,
                null,
                null,
                null,
                ConsentStatus.GRANTED
        ));

        verify(repository).save(patient);
        assertThat(result.contactPreference()).isEqualTo(ContactPreference.EMAIL);
    }

    @Test
    void changesStatusAndPersistsSameEntity() {
        Patient patient = patient();
        when(repository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));

        var result = service.changeStatus(
                new ChangePatientStatusCommand(OWNER, PATIENT_ID, PatientStatus.INACTIVE));

        verify(repository).save(patient);
        assertThat(result.status()).isEqualTo(PatientStatus.INACTIVE);
    }

    @Test
    void rejectsMissingPatientWithoutSaving() {
        when(repository.findById(PATIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(new GetPatientQuery(OWNER, PATIENT_ID)))
                .isInstanceOf(PatientNotFoundException.class);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsPatientOwnedByAnotherPsychologistWithoutSaving() {
        when(repository.findById(PATIENT_ID)).thenReturn(Optional.of(patient()));

        assertThatThrownBy(() -> service.changeStatus(
                new ChangePatientStatusCommand(OTHER, PATIENT_ID, PatientStatus.INACTIVE)))
                .isInstanceOf(PatientAccessDeniedException.class);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
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
