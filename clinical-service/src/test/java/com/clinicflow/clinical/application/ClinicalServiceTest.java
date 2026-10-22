package com.clinicflow.clinical.application;

import com.clinicflow.clinical.application.commands.CareGoalInput;
import com.clinicflow.clinical.application.commands.CreateClinicalCaseCommand;
import com.clinicflow.clinical.application.commands.ProcessAppointmentCompletedCommand;
import com.clinicflow.clinical.application.commands.PutCarePlanCommand;
import com.clinicflow.clinical.application.commands.ChangeClinicalCaseStatusCommand;
import com.clinicflow.clinical.application.commands.CreateSessionRecordCommand;
import com.clinicflow.clinical.application.commands.SignSessionRecordCommand;
import com.clinicflow.clinical.application.commands.UpdatePracticeProfileCommand;
import com.clinicflow.clinical.application.commands.UpdateSessionRecordCommand;
import com.clinicflow.clinical.application.exceptions.ActiveClinicalCaseExistsException;
import com.clinicflow.clinical.application.exceptions.AppointmentAlreadyRecordedException;
import com.clinicflow.clinical.application.exceptions.AppointmentNotFoundException;
import com.clinicflow.clinical.application.exceptions.AppointmentPatientMismatchException;
import com.clinicflow.clinical.application.exceptions.CarePlanNotFoundException;
import com.clinicflow.clinical.application.exceptions.ClinicalAccessDeniedException;
import com.clinicflow.clinical.application.exceptions.ClinicalCaseNotFoundException;
import com.clinicflow.clinical.application.exceptions.OwnershipCheckUnavailableException;
import com.clinicflow.clinical.application.exceptions.PatientNotFoundException;
import com.clinicflow.clinical.application.exceptions.SessionRecordNotFoundException;
import com.clinicflow.clinical.application.exceptions.StaleResourceVersionException;
import com.clinicflow.clinical.application.mapping.ClinicalApplicationMapper;
import com.clinicflow.clinical.application.ports.AppointmentDirectory;
import com.clinicflow.clinical.application.ports.CarePlanRepository;
import com.clinicflow.clinical.application.ports.ClinicalCaseRepository;
import com.clinicflow.clinical.application.ports.PatientDirectory;
import com.clinicflow.clinical.application.ports.PracticeProfileRepository;
import com.clinicflow.clinical.application.ports.SessionRecordLinkConflictRepository;
import com.clinicflow.clinical.application.ports.SessionRecordRepository;
import com.clinicflow.clinical.application.queries.GetPatientClinicalHistoryQuery;
import com.clinicflow.clinical.application.queries.GetCarePlanQuery;
import com.clinicflow.clinical.application.queries.GetClinicalCaseQuery;
import com.clinicflow.clinical.application.queries.GetPracticeProfileQuery;
import com.clinicflow.clinical.application.queries.ListClinicalCasesQuery;
import com.clinicflow.clinical.application.queries.ListSessionRecordsQuery;
import com.clinicflow.clinical.application.results.CarePlanResult;
import com.clinicflow.clinical.application.results.ClinicalCaseResult;
import com.clinicflow.clinical.application.results.PatientClinicalHistoryResult;
import com.clinicflow.clinical.application.results.PracticeProfileResult;
import com.clinicflow.clinical.application.results.SessionRecordResult;
import com.clinicflow.clinical.domain.AppointmentId;
import com.clinicflow.clinical.domain.AttendanceStatus;
import com.clinicflow.clinical.domain.CareGoal;
import com.clinicflow.clinical.domain.CareGoalId;
import com.clinicflow.clinical.domain.CarePlan;
import com.clinicflow.clinical.domain.CarePlanId;
import com.clinicflow.clinical.domain.ClinicalCase;
import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.DomainEventId;
import com.clinicflow.clinical.domain.GoalStatus;
import com.clinicflow.clinical.domain.ClinicalCaseStatus;
import com.clinicflow.clinical.domain.LinkConflictSource;
import com.clinicflow.clinical.domain.NoteStatus;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PracticeProfile;
import com.clinicflow.clinical.domain.PsychologistId;
import com.clinicflow.clinical.domain.SessionModality;
import com.clinicflow.clinical.domain.SessionRecord;
import com.clinicflow.clinical.domain.SessionRecordId;
import com.clinicflow.clinical.domain.SessionRecordLinkConflict;
import com.clinicflow.clinical.domain.SessionRecordNotSignableException;
import com.clinicflow.clinical.domain.SignedSessionRecordException;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicalServiceTest {
    private static final PsychologistId PSYCHOLOGIST_ID =
            new PsychologistId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private static final PsychologistId OTHER_PSYCHOLOGIST = new PsychologistId(UUID.randomUUID());
    private static final PatientId PATIENT_ID =
            new PatientId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    private static final PatientId OTHER_PATIENT = new PatientId(UUID.randomUUID());
    private static final ClinicalCaseId CASE_ID =
            new ClinicalCaseId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));
    private static final AppointmentId APPOINTMENT_ID =
            new AppointmentId(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"));
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final LocalDate REVIEW_DATE = LocalDate.of(2026, 7, 1);

    @Mock private PracticeProfileRepository practiceProfiles;
    @Mock private ClinicalCaseRepository clinicalCases;
    @Mock private CarePlanRepository carePlans;
    @Mock private SessionRecordRepository sessionRecords;
    @Mock private SessionRecordLinkConflictRepository linkConflicts;
    @Mock private PatientDirectory patientDirectory;
    @Mock private AppointmentDirectory appointmentDirectory;

    private ClinicalService service;

    @BeforeEach
    void setUp() {
        service = new ClinicalService(practiceProfiles, clinicalCases, carePlans, sessionRecords, linkConflicts,
                patientDirectory, appointmentDirectory, Mappers.getMapper(ClinicalApplicationMapper.class));
        // Like persistence: a new record gets version 0, every save of a stored one increments it.
        lenient().when(practiceProfiles.save(any(PracticeProfile.class)))
                .thenAnswer(call -> stored(call.<PracticeProfile>getArgument(0)));
        lenient().when(clinicalCases.save(any(ClinicalCase.class)))
                .thenAnswer(call -> stored(call.<ClinicalCase>getArgument(0)));
        lenient().when(carePlans.save(any(CarePlan.class)))
                .thenAnswer(call -> stored(call.<CarePlan>getArgument(0)));
        lenient().when(sessionRecords.save(any(SessionRecord.class)))
                .thenAnswer(call -> stored(call.<SessionRecord>getArgument(0)));
    }

    @Test
    void createsCaseWhenNoOpenCaseExists() {
        // Arrange
        when(patientDirectory.isOwnedBy(PATIENT_ID, PSYCHOLOGIST_ID)).thenReturn(true);
        when(clinicalCases.existsOpenByPsychologistIdAndPatientId(PSYCHOLOGIST_ID, PATIENT_ID)).thenReturn(false);
        var command = new CreateClinicalCaseCommand(PSYCHOLOGIST_ID, PATIENT_ID, "Anxiety symptoms");

        // Act
        ClinicalCaseResult result = service.createClinicalCase(command);

        // Assert
        ArgumentCaptor<ClinicalCase> captor = ArgumentCaptor.forClass(ClinicalCase.class);
        verify(clinicalCases).save(captor.capture());
        assertThat(result.id()).isEqualTo(captor.getValue().id().value());
        assertThat(result.patientId()).isEqualTo(PATIENT_ID.value());
    }

    @Test
    void rejectsCaseWhenAnOpenCaseExists() {
        // Arrange
        when(patientDirectory.isOwnedBy(PATIENT_ID, PSYCHOLOGIST_ID)).thenReturn(true);
        when(clinicalCases.existsOpenByPsychologistIdAndPatientId(PSYCHOLOGIST_ID, PATIENT_ID)).thenReturn(true);
        var command = new CreateClinicalCaseCommand(PSYCHOLOGIST_ID, PATIENT_ID, "Another concern");

        // Act
        Throwable thrown = catchThrowable(() -> service.createClinicalCase(command));

        // Assert
        assertThat(thrown).isInstanceOf(ActiveClinicalCaseExistsException.class);
    }

    @Test
    void rejectsCaseForPatientOwnedBySomeoneElse() {
        // Arrange
        when(patientDirectory.isOwnedBy(PATIENT_ID, PSYCHOLOGIST_ID)).thenReturn(false);
        var command = new CreateClinicalCaseCommand(PSYCHOLOGIST_ID, PATIENT_ID, "Anxiety symptoms");

        // Act
        Throwable thrown = catchThrowable(() -> service.createClinicalCase(command));

        // Assert
        assertThat(thrown).isInstanceOf(PatientNotFoundException.class);
    }

    @Test
    void caseForForeignPatientTouchesNoClinicalRecords() {
        // Arrange
        when(patientDirectory.isOwnedBy(PATIENT_ID, PSYCHOLOGIST_ID)).thenReturn(false);
        var command = new CreateClinicalCaseCommand(PSYCHOLOGIST_ID, PATIENT_ID, "Anxiety symptoms");

        // Act
        catchThrowable(() -> service.createClinicalCase(command));

        // Assert
        verifyNoInteractions(clinicalCases);
    }

    @Test
    void caseCreationReportsUnavailableOwnershipCheck() {
        // Arrange
        when(patientDirectory.isOwnedBy(PATIENT_ID, PSYCHOLOGIST_ID))
                .thenThrow(new OwnershipCheckUnavailableException("patient-service unavailable"));
        var command = new CreateClinicalCaseCommand(PSYCHOLOGIST_ID, PATIENT_ID, "Anxiety symptoms");

        // Act
        Throwable thrown = catchThrowable(() -> service.createClinicalCase(command));

        // Assert
        assertThat(thrown).isInstanceOf(OwnershipCheckUnavailableException.class);
    }

    @Test
    void caseCreationStoresNothingWhenOwnershipCannotBeVerified() {
        // Arrange
        when(patientDirectory.isOwnedBy(PATIENT_ID, PSYCHOLOGIST_ID))
                .thenThrow(new OwnershipCheckUnavailableException("patient-service unavailable"));
        var command = new CreateClinicalCaseCommand(PSYCHOLOGIST_ID, PATIENT_ID, "Anxiety symptoms");

        // Act
        catchThrowable(() -> service.createClinicalCase(command));

        // Assert
        verifyNoInteractions(clinicalCases);
    }

    @Test
    void createsCarePlanForOwnedCase() {
        // Arrange
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(clinicalCase()));
        when(carePlans.findByClinicalCaseId(CASE_ID)).thenReturn(Optional.empty());
        var command = carePlanCommand(new CareGoalInput(null, "Practice exposure", null, GoalStatus.IN_PROGRESS, 20));

        // Act
        CarePlanResult result = service.putCarePlan(command);

        // Assert
        assertThat(result.goals()).singleElement().satisfies(goal -> {
            assertThat(goal.description()).isEqualTo("Practice exposure");
            assertThat(goal.progressPercentage()).isEqualTo(20);
        });
    }

    @Test
    void storesNewCarePlanAgainstTheCase() {
        // Arrange
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(clinicalCase()));
        when(carePlans.findByClinicalCaseId(CASE_ID)).thenReturn(Optional.empty());
        var command = carePlanCommand(new CareGoalInput(null, "Practice exposure", null, GoalStatus.IN_PROGRESS, 20));

        // Act
        service.putCarePlan(command);

        // Assert
        ArgumentCaptor<CarePlan> captor = ArgumentCaptor.forClass(CarePlan.class);
        verify(carePlans).save(captor.capture());
        assertThat(captor.getValue().clinicalCaseId()).isEqualTo(CASE_ID);
    }

    @Test
    void completedAppointmentCreatesSessionLinkedToOpenCaseWithProfileDuration() {
        // Arrange
        when(sessionRecords.findByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.empty());
        when(clinicalCases.findOpenByPsychologistIdAndPatientId(PSYCHOLOGIST_ID, PATIENT_ID))
                .thenReturn(Optional.of(clinicalCase()));
        when(practiceProfiles.findByPsychologistId(PSYCHOLOGIST_ID)).thenReturn(Optional.of(
                stored(PracticeProfile.create(PSYCHOLOGIST_ID, null, ZoneId.of("UTC"), 60, null, true, NOW))));

        // Act
        service.processAppointmentCompleted(appointmentCompleted());

        // Assert
        ArgumentCaptor<SessionRecord> captor = ArgumentCaptor.forClass(SessionRecord.class);
        verify(sessionRecords).save(captor.capture());
        assertThat(captor.getValue().clinicalCaseId()).contains(CASE_ID);
        assertThat(captor.getValue().durationMinutes()).isEqualTo(60);
    }

    @Test
    void completedAppointmentWithExistingSessionCreatesNothing() {
        // Arrange
        SessionRecord existing = stored(SessionRecord.fromCompletedAppointment(new SessionRecordId(UUID.randomUUID()),
                CASE_ID, PSYCHOLOGIST_ID, PATIENT_ID, APPOINTMENT_ID, NOW, SessionModality.ONLINE, 60, NOW));
        when(sessionRecords.findByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.of(existing));

        // Act
        service.processAppointmentCompleted(appointmentCompleted());

        // Assert
        verify(sessionRecords, never()).save(any());
    }

    @Test
    void completedAppointmentWithExistingSessionRecordsNoConflict() {
        // Arrange
        SessionRecord existing = SessionRecord.fromCompletedAppointment(new SessionRecordId(UUID.randomUUID()),
                CASE_ID, PSYCHOLOGIST_ID, PATIENT_ID, APPOINTMENT_ID, NOW, SessionModality.ONLINE, 60, NOW);
        when(sessionRecords.findByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.of(existing));

        // Act
        service.processAppointmentCompleted(appointmentCompleted());

        // Assert
        verifyNoInteractions(linkConflicts);
    }

    @ParameterizedTest(name = "claimed by {0}")
    @MethodSource("claimsOfAnotherPsychologistOrPatient")
    void completedAppointmentClaimedByAnotherRecordReleasesTheClaim(String claimant, PsychologistId psychologist,
                                                                    PatientId patient) {
        // Arrange
        SessionRecord claimed = claimingRecord(psychologist, patient);
        arrangeCompletedAppointmentClaimedBy(claimed);

        // Act
        service.processAppointmentCompleted(appointmentCompleted());

        // Assert
        assertThat(claimed.appointmentId()).isEmpty();
        verify(sessionRecords).save(claimed);
    }

    @Test
    void completedAppointmentClaimedByAnotherPsychologistCreatesTheOwnersRecord() {
        // Arrange
        arrangeCompletedAppointmentClaimedBy(claimingRecord(OTHER_PSYCHOLOGIST, OTHER_PATIENT));

        // Act
        service.processAppointmentCompleted(appointmentCompleted());

        // Assert
        ArgumentCaptor<SessionRecord> captor = ArgumentCaptor.forClass(SessionRecord.class);
        verify(sessionRecords, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().getLast()).satisfies(created -> {
            assertThat(created.psychologistId()).isEqualTo(PSYCHOLOGIST_ID);
            assertThat(created.patientId()).isEqualTo(PATIENT_ID);
            assertThat(created.appointmentId()).contains(APPOINTMENT_ID);
        });
    }

    @Test
    void completedAppointmentClaimRecordsTheConflictBeforeReleasingAndRelinking() {
        // Arrange
        SessionRecord claimed = claimingRecord(OTHER_PSYCHOLOGIST, OTHER_PATIENT);
        arrangeCompletedAppointmentClaimedBy(claimed);

        // Act
        service.processAppointmentCompleted(appointmentCompleted());

        // Assert
        InOrder order = inOrder(linkConflicts, sessionRecords);
        order.verify(linkConflicts).save(any(SessionRecordLinkConflict.class));
        order.verify(sessionRecords).save(claimed);
        order.verify(sessionRecords).save(argThat(record -> !record.equals(claimed)));
    }

    @Test
    void completedAppointmentClaimIsAuditedWithTheAppointmentOwner() {
        // Arrange
        SessionRecord claimed = claimingRecord(OTHER_PSYCHOLOGIST, OTHER_PATIENT);
        arrangeCompletedAppointmentClaimedBy(claimed);

        // Act
        service.processAppointmentCompleted(appointmentCompleted());

        // Assert
        ArgumentCaptor<SessionRecordLinkConflict> captor = ArgumentCaptor.forClass(SessionRecordLinkConflict.class);
        verify(linkConflicts).save(captor.capture());
        assertThat(captor.getValue()).satisfies(conflict -> {
            assertThat(conflict.sessionRecordId()).isEqualTo(claimed.id());
            assertThat(conflict.appointmentId()).isEqualTo(APPOINTMENT_ID);
            assertThat(conflict.appointmentPsychologistId()).isEqualTo(PSYCHOLOGIST_ID);
            assertThat(conflict.appointmentPatientId()).isEqualTo(PATIENT_ID);
            assertThat(conflict.source()).isEqualTo(LinkConflictSource.APPOINTMENT_COMPLETED);
        });
    }

    @Test
    void completedAppointmentClaimIsLoggedWithItsResolution() {
        // Arrange
        SessionRecord claimed = claimingRecord(OTHER_PSYCHOLOGIST, PATIENT_ID);
        arrangeCompletedAppointmentClaimedBy(claimed);
        ListAppender<ILoggingEvent> logs = captureServiceLogs();

        // Act
        try {
            service.processAppointmentCompleted(appointmentCompleted());
        } finally {
            releaseServiceLogs(logs);
        }

        // Assert
        assertThat(logs.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
                .isEqualTo("session_record_appointment_conflict appointmentId=" + APPOINTMENT_ID.value()
                        + " sessionRecordId=" + claimed.id().value()
                        + " source=APPOINTMENT_COMPLETED resolution=link_released"));
    }

    @Test
    void assemblesPatientHistoryFromOwnedRecords() {
        // Arrange
        when(clinicalCases.findByPsychologistIdAndPatientId(PSYCHOLOGIST_ID, PATIENT_ID))
                .thenReturn(List.of(clinicalCase()));
        when(carePlans.findByClinicalCaseId(CASE_ID)).thenReturn(Optional.empty());
        when(sessionRecords.findByPsychologistIdAndPatientId(PSYCHOLOGIST_ID, PATIENT_ID))
                .thenReturn(List.of(stored(SessionRecord.fromCompletedAppointment(
                        new SessionRecordId(UUID.randomUUID()), CASE_ID,
                        PSYCHOLOGIST_ID, PATIENT_ID, new AppointmentId(UUID.randomUUID()),
                        NOW, SessionModality.IN_PERSON, 50, NOW))));
        var query = new GetPatientClinicalHistoryQuery(PSYCHOLOGIST_ID, PATIENT_ID);

        // Act
        PatientClinicalHistoryResult result = service.getPatientClinicalHistory(query);

        // Assert
        assertThat(result.cases()).hasSize(1);
        assertThat(result.sessionRecords()).hasSize(1);
    }

    @Test
    void readsFallbackPracticeProfileWhenNoneIsStored() {
        // Arrange
        var query = new GetPracticeProfileQuery(PSYCHOLOGIST_ID);

        // Act
        PracticeProfileResult fallback = service.getPracticeProfile(query);

        // Assert
        assertThat(fallback.timezone()).isEqualTo("UTC");
        assertThat(fallback.defaultSessionDurationMinutes()).isEqualTo(50);
    }

    @Test
    void createsPracticeProfileWhenNoneIsStored() {
        // Arrange
        var command = new UpdatePracticeProfileCommand(
                PSYCHOLOGIST_ID, "OPP 123", ZoneId.of("Europe/Lisbon"), 45, "Lisbon", true, null);

        // Act
        PracticeProfileResult created = service.updatePracticeProfile(command);

        // Assert
        assertThat(created.professionalRegistration()).isEqualTo("OPP 123");
        verify(practiceProfiles).save(any(PracticeProfile.class));
    }

    @Test
    void updatesStoredPracticeProfile() {
        // Arrange
        PracticeProfile existing = stored(PracticeProfile.create(
                PSYCHOLOGIST_ID, null, ZoneId.of("UTC"), 50, null, false, NOW));
        when(practiceProfiles.findByPsychologistId(PSYCHOLOGIST_ID)).thenReturn(Optional.of(existing));
        var command = new UpdatePracticeProfileCommand(
                PSYCHOLOGIST_ID, "OPP 456", ZoneId.of("Europe/Lisbon"), 60, "Porto", true, 0L);

        // Act
        PracticeProfileResult updated = service.updatePracticeProfile(command);

        // Assert
        assertThat(updated.defaultSessionDurationMinutes()).isEqualTo(60);
        assertThat(updated.primaryLocation()).isEqualTo("Porto");
    }

    @Test
    void listsAllCasesOfThePsychologist() {
        // Arrange
        when(clinicalCases.findByPsychologistId(PSYCHOLOGIST_ID)).thenReturn(List.of(clinicalCase()));
        var query = new ListClinicalCasesQuery(PSYCHOLOGIST_ID, null, null);

        // Act
        List<ClinicalCaseResult> cases = service.listClinicalCases(query);

        // Assert
        assertThat(cases).hasSize(1);
    }

    @Test
    void listsCasesByStatus() {
        // Arrange
        when(clinicalCases.findByPsychologistIdAndStatus(PSYCHOLOGIST_ID, ClinicalCaseStatus.ACTIVE))
                .thenReturn(List.of(clinicalCase()));
        var query = new ListClinicalCasesQuery(PSYCHOLOGIST_ID, null, ClinicalCaseStatus.ACTIVE);

        // Act
        List<ClinicalCaseResult> cases = service.listClinicalCases(query);

        // Assert
        assertThat(cases).hasSize(1);
    }

    @Test
    void listsCasesByPatient() {
        // Arrange
        when(clinicalCases.findByPsychologistIdAndPatientId(PSYCHOLOGIST_ID, PATIENT_ID))
                .thenReturn(List.of(clinicalCase()));
        var query = new ListClinicalCasesQuery(PSYCHOLOGIST_ID, PATIENT_ID, null);

        // Act
        List<ClinicalCaseResult> cases = service.listClinicalCases(query);

        // Assert
        assertThat(cases).hasSize(1);
    }

    @Test
    void listsPatientCasesFilteredByStatus() {
        // Arrange
        when(clinicalCases.findByPsychologistIdAndPatientId(PSYCHOLOGIST_ID, PATIENT_ID))
                .thenReturn(List.of(clinicalCase()));
        var query = new ListClinicalCasesQuery(PSYCHOLOGIST_ID, PATIENT_ID, ClinicalCaseStatus.DISCHARGED);

        // Act
        List<ClinicalCaseResult> cases = service.listClinicalCases(query);

        // Assert
        assertThat(cases).isEmpty();
    }

    @Test
    void getsOwnedClinicalCase() {
        // Arrange
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(clinicalCase()));
        var query = new GetClinicalCaseQuery(PSYCHOLOGIST_ID, CASE_ID);

        // Act
        ClinicalCaseResult result = service.getClinicalCase(query);

        // Assert
        assertThat(result.id()).isEqualTo(CASE_ID.value());
    }

    @Test
    void reportsMissingClinicalCase() {
        // Arrange
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.empty());
        var query = new GetClinicalCaseQuery(PSYCHOLOGIST_ID, CASE_ID);

        // Act
        Throwable thrown = catchThrowable(() -> service.getClinicalCase(query));

        // Assert
        assertThat(thrown).isInstanceOf(ClinicalCaseNotFoundException.class);
    }

    @Test
    void answersAnotherPsychologistsCaseAsNotFound() {
        // Arrange
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(clinicalCase()));
        var query = new GetClinicalCaseQuery(OTHER_PSYCHOLOGIST, CASE_ID);

        // Act
        Throwable thrown = catchThrowable(() -> service.getClinicalCase(query));

        // Assert
        assertThat(thrown).isInstanceOf(ClinicalCaseNotFoundException.class);
    }

    @Test
    void changesStatusOfOwnedCaseAndSavesIt() {
        // Arrange
        ClinicalCase owned = clinicalCase();
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(owned));
        var command = new ChangeClinicalCaseStatusCommand(PSYCHOLOGIST_ID, CASE_ID, ClinicalCaseStatus.DISCHARGED, 0);

        // Act
        ClinicalCaseResult discharged = service.changeClinicalCaseStatus(command);

        // Assert
        assertThat(discharged.status()).isEqualTo(ClinicalCaseStatus.DISCHARGED);
        verify(clinicalCases).save(owned);
    }

    @Test
    void getsCarePlanOfOwnedCase() {
        // Arrange
        ClinicalCase owned = clinicalCase();
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(owned));
        when(carePlans.findByClinicalCaseId(CASE_ID)).thenReturn(Optional.of(carePlan(owned,
                new CareGoalId(UUID.randomUUID()))));
        var query = new GetCarePlanQuery(PSYCHOLOGIST_ID, CASE_ID);

        // Act
        CarePlanResult result = service.getCarePlan(query);

        // Assert
        assertThat(result.therapeuticFocus()).isEqualTo("Focus");
    }

    @Test
    void reportsMissingCarePlan() {
        // Arrange
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(clinicalCase()));
        when(carePlans.findByClinicalCaseId(CASE_ID)).thenReturn(Optional.empty());
        var query = new GetCarePlanQuery(PSYCHOLOGIST_ID, CASE_ID);

        // Act
        Throwable thrown = catchThrowable(() -> service.getCarePlan(query));

        // Assert
        assertThat(thrown).isInstanceOf(CarePlanNotFoundException.class);
    }

    @Test
    void createsSessionRecordForOwnedCase() {
        // Arrange
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(clinicalCase()));
        var command = new CreateSessionRecordCommand(
                PSYCHOLOGIST_ID, CASE_ID, null, NOW, SessionModality.ONLINE, 50,
                AttendanceStatus.ATTENDED, "Summary", "Focus", "CBT", "Homework", "Next");

        // Act
        SessionRecordResult created = service.createSessionRecord(command);

        // Assert
        assertThat(created.summary()).isEqualTo("Summary");
        verify(sessionRecords).save(any(SessionRecord.class));
    }

    @Test
    void sessionRecordWithoutAppointmentNeedsNoAppointmentLookup() {
        // Arrange
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(clinicalCase()));

        // Act
        service.createSessionRecord(sessionCommand(null));

        // Assert
        verifyNoInteractions(appointmentDirectory);
    }

    @Test
    void createsSessionRecordLinkedToOwnedAppointmentOfTheCasePatient() {
        // Arrange
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(clinicalCase()));
        when(appointmentDirectory.findPatientOfOwnedAppointment(APPOINTMENT_ID, PSYCHOLOGIST_ID))
                .thenReturn(Optional.of(PATIENT_ID));
        when(sessionRecords.findByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.empty());

        // Act
        SessionRecordResult created = service.createSessionRecord(sessionCommand(APPOINTMENT_ID));

        // Assert
        assertThat(created.appointmentId()).isEqualTo(APPOINTMENT_ID.value());
    }

    @Test
    void rejectsSessionLinkToUnknownOrForeignAppointment() {
        // Arrange
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(clinicalCase()));
        when(appointmentDirectory.findPatientOfOwnedAppointment(APPOINTMENT_ID, PSYCHOLOGIST_ID))
                .thenReturn(Optional.empty());

        // Act
        Throwable thrown = catchThrowable(() -> service.createSessionRecord(sessionCommand(APPOINTMENT_ID)));

        // Assert
        assertThat(thrown).isInstanceOf(AppointmentNotFoundException.class);
    }

    @Test
    void foreignAppointmentLinkDoesNotRevealWhetherItWasRecorded() {
        // Arrange
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(clinicalCase()));
        when(appointmentDirectory.findPatientOfOwnedAppointment(APPOINTMENT_ID, PSYCHOLOGIST_ID))
                .thenReturn(Optional.empty());

        // Act
        catchThrowable(() -> service.createSessionRecord(sessionCommand(APPOINTMENT_ID)));

        // Assert
        verifyNoInteractions(sessionRecords);
    }

    @Test
    void rejectsSessionLinkToAppointmentOfAnotherPatient() {
        // Arrange
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(clinicalCase()));
        when(appointmentDirectory.findPatientOfOwnedAppointment(APPOINTMENT_ID, PSYCHOLOGIST_ID))
                .thenReturn(Optional.of(new PatientId(UUID.randomUUID())));

        // Act
        Throwable thrown = catchThrowable(() -> service.createSessionRecord(sessionCommand(APPOINTMENT_ID)));

        // Assert
        assertThat(thrown).isInstanceOf(AppointmentPatientMismatchException.class);
    }

    @Test
    void rejectsSessionLinkToAppointmentThatAlreadyHasARecord() {
        // Arrange
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(clinicalCase()));
        when(appointmentDirectory.findPatientOfOwnedAppointment(APPOINTMENT_ID, PSYCHOLOGIST_ID))
                .thenReturn(Optional.of(PATIENT_ID));
        when(sessionRecords.findByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.of(
                stored(SessionRecord.fromCompletedAppointment(new SessionRecordId(UUID.randomUUID()), CASE_ID,
                        PSYCHOLOGIST_ID, PATIENT_ID, APPOINTMENT_ID, NOW, SessionModality.ONLINE, 50, NOW))));

        // Act
        Throwable thrown = catchThrowable(() -> service.createSessionRecord(sessionCommand(APPOINTMENT_ID)));

        // Assert
        assertThat(thrown).isInstanceOf(AppointmentAlreadyRecordedException.class);
    }

    @Test
    void ownAppointmentAlreadyRecordedReleasesNothing() {
        // Arrange
        SessionRecord recorded = SessionRecord.fromCompletedAppointment(new SessionRecordId(UUID.randomUUID()),
                CASE_ID, PSYCHOLOGIST_ID, PATIENT_ID, APPOINTMENT_ID, NOW, SessionModality.ONLINE, 50, NOW);
        arrangeVerifiedLinkClaimedBy(recorded);

        // Act
        catchThrowable(() -> service.createSessionRecord(sessionCommand(APPOINTMENT_ID)));

        // Assert
        assertThat(recorded.appointmentId()).contains(APPOINTMENT_ID);
        verifyNoInteractions(linkConflicts);
    }

    @ParameterizedTest(name = "claimed by {0}")
    @MethodSource("claimsOfAnotherPsychologistOrPatient")
    void verifiedLinkCreatesTheOwnersRecordWhenAnotherRecordClaimsTheAppointment(String claimant,
                                                                                 PsychologistId psychologist,
                                                                                 PatientId patient) {
        // Arrange
        arrangeVerifiedLinkClaimedBy(claimingRecord(psychologist, patient));

        // Act
        SessionRecordResult created = service.createSessionRecord(sessionCommand(APPOINTMENT_ID));

        // Assert
        assertThat(created.appointmentId()).isEqualTo(APPOINTMENT_ID.value());
        assertThat(created.psychologistId()).isEqualTo(PSYCHOLOGIST_ID.value());
    }

    @Test
    void verifiedLinkReleasesAnotherPsychologistsClaim() {
        // Arrange
        SessionRecord claimed = claimingRecord(OTHER_PSYCHOLOGIST, OTHER_PATIENT);
        arrangeVerifiedLinkClaimedBy(claimed);

        // Act
        service.createSessionRecord(sessionCommand(APPOINTMENT_ID));

        // Assert
        assertThat(claimed.appointmentId()).isEmpty();
        InOrder order = inOrder(linkConflicts, sessionRecords);
        order.verify(linkConflicts).save(any(SessionRecordLinkConflict.class));
        order.verify(sessionRecords).save(claimed);
        order.verify(sessionRecords).save(argThat(record -> !record.equals(claimed)));
    }

    @Test
    void verifiedLinkAuditsTheReleasedClaimAsAManualLink() {
        // Arrange
        SessionRecord claimed = claimingRecord(OTHER_PSYCHOLOGIST, OTHER_PATIENT);
        arrangeVerifiedLinkClaimedBy(claimed);

        // Act
        service.createSessionRecord(sessionCommand(APPOINTMENT_ID));

        // Assert
        ArgumentCaptor<SessionRecordLinkConflict> captor = ArgumentCaptor.forClass(SessionRecordLinkConflict.class);
        verify(linkConflicts).save(captor.capture());
        assertThat(captor.getValue()).satisfies(conflict -> {
            assertThat(conflict.sessionRecordId()).isEqualTo(claimed.id());
            assertThat(conflict.appointmentId()).isEqualTo(APPOINTMENT_ID);
            assertThat(conflict.appointmentPsychologistId()).isEqualTo(PSYCHOLOGIST_ID);
            assertThat(conflict.appointmentPatientId()).isEqualTo(PATIENT_ID);
            assertThat(conflict.source()).isEqualTo(LinkConflictSource.MANUAL_LINK);
        });
    }

    @Test
    void invalidSessionRecordLeavesAnotherPsychologistsClaimInPlace() {
        // Arrange
        SessionRecord claimed = claimingRecord(OTHER_PSYCHOLOGIST, OTHER_PATIENT);
        arrangeVerifiedLinkClaimedBy(claimed);
        var command = new CreateSessionRecordCommand(PSYCHOLOGIST_ID, CASE_ID, APPOINTMENT_ID, NOW,
                SessionModality.ONLINE, 481, AttendanceStatus.ATTENDED, "Summary", null, null, null, null);

        // Act
        catchThrowable(() -> service.createSessionRecord(command));

        // Assert
        assertThat(claimed.appointmentId()).contains(APPOINTMENT_ID);
        verifyNoInteractions(linkConflicts);
    }

    @Test
    void rejectedAppointmentLinkStoresNoSessionRecord() {
        // Arrange
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(clinicalCase()));
        when(appointmentDirectory.findPatientOfOwnedAppointment(APPOINTMENT_ID, PSYCHOLOGIST_ID))
                .thenReturn(Optional.of(new PatientId(UUID.randomUUID())));

        // Act
        catchThrowable(() -> service.createSessionRecord(sessionCommand(APPOINTMENT_ID)));

        // Assert
        verify(sessionRecords, never()).save(any());
    }

    @Test
    void listsSessionRecordsOfOwnedCase() {
        // Arrange
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(clinicalCase()));
        when(sessionRecords.findByClinicalCaseId(CASE_ID)).thenReturn(List.of(draftSession(PSYCHOLOGIST_ID, "Summary")));
        var query = new ListSessionRecordsQuery(PSYCHOLOGIST_ID, CASE_ID);

        // Act
        List<SessionRecordResult> sessions = service.listSessionRecords(query);

        // Assert
        assertThat(sessions).hasSize(1);
    }

    @Test
    void updatesOwnedDraftSessionWithoutSigningIt() {
        // Arrange
        SessionRecord session = draftSession(PSYCHOLOGIST_ID, "Summary");
        when(sessionRecords.findById(session.id())).thenReturn(Optional.of(session));
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(clinicalCase()));
        var command = new UpdateSessionRecordCommand(
                PSYCHOLOGIST_ID, session.id(), CASE_ID, NOW.plusSeconds(60), SessionModality.IN_PERSON, 55,
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, "Updated", null, null, null, null, 0);

        // Act
        SessionRecordResult updated = service.updateSessionRecord(command);

        // Assert
        assertThat(updated.noteStatus()).isEqualTo(NoteStatus.DRAFT);
        assertThat(updated.durationMinutes()).isEqualTo(55);
        assertThat(updated.signedAt()).isNull();
        assertThat(updated.signedBy()).isNull();
    }

    @Test
    void reportsMissingSessionRecordOnUpdate() {
        // Arrange
        SessionRecordId missingId = new SessionRecordId(UUID.randomUUID());
        when(sessionRecords.findById(missingId)).thenReturn(Optional.empty());
        var command = new UpdateSessionRecordCommand(
                PSYCHOLOGIST_ID, missingId, null, NOW, SessionModality.ONLINE, 50,
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, null, null, null, null, null, 0);

        // Act
        Throwable thrown = catchThrowable(() -> service.updateSessionRecord(command));

        // Assert
        assertThat(thrown).isInstanceOf(SessionRecordNotFoundException.class);
    }

    @Test
    void signingAStaleRevisionIsRejected() {
        // Arrange
        SessionRecord session = draftSession(PSYCHOLOGIST_ID, "Summary");
        when(sessionRecords.findById(session.id())).thenReturn(Optional.of(session));

        // Act
        Throwable thrown = catchThrowable(() -> service.signSessionRecord(
                new SignSessionRecordCommand(PSYCHOLOGIST_ID, session.id(), 1)));

        // Assert
        assertThat(thrown).isInstanceOf(StaleResourceVersionException.class);
    }

    @Test
    void signingAStaleRevisionLeavesTheNoteUnsigned() {
        // Arrange
        SessionRecord session = draftSession(PSYCHOLOGIST_ID, "Summary");
        when(sessionRecords.findById(session.id())).thenReturn(Optional.of(session));

        // Act
        catchThrowable(() -> service.signSessionRecord(new SignSessionRecordCommand(PSYCHOLOGIST_ID, session.id(), 1)));

        // Assert
        assertThat(session.noteStatus()).isEqualTo(NoteStatus.DRAFT);
        verify(sessionRecords, never()).save(any());
    }

    @Test
    void foreignNoteLooksMissingEvenWithAStaleVersion() {
        // Arrange
        SessionRecord foreign = draftSession(OTHER_PSYCHOLOGIST, "Summary");
        when(sessionRecords.findById(foreign.id())).thenReturn(Optional.of(foreign));

        // Act
        Throwable thrown = catchThrowable(() -> service.signSessionRecord(
                new SignSessionRecordCommand(PSYCHOLOGIST_ID, foreign.id(), 1)));

        // Assert
        assertThat(thrown).isInstanceOf(SessionRecordNotFoundException.class);
    }

    @Test
    void staleSessionEditIsNeverSaved() {
        // Arrange
        SessionRecord session = draftSession(PSYCHOLOGIST_ID, "Summary");
        when(sessionRecords.findById(session.id())).thenReturn(Optional.of(session));
        var command = new UpdateSessionRecordCommand(PSYCHOLOGIST_ID, session.id(), null, NOW, SessionModality.ONLINE,
                50, AttendanceStatus.ATTENDED, NoteStatus.DRAFT, "Older text", null, null, null, null, 1);

        // Act
        Throwable thrown = catchThrowable(() -> service.updateSessionRecord(command));

        // Assert
        assertThat(thrown).isInstanceOf(StaleResourceVersionException.class);
        verify(sessionRecords, never()).save(any());
    }

    @Test
    void sessionEditReturnsTheVersionAssignedOnSave() {
        // Arrange
        SessionRecord session = draftSession(PSYCHOLOGIST_ID, "Summary");
        when(sessionRecords.findById(session.id())).thenReturn(Optional.of(session));
        var command = new UpdateSessionRecordCommand(PSYCHOLOGIST_ID, session.id(), null, NOW, SessionModality.ONLINE,
                50, AttendanceStatus.ATTENDED, NoteStatus.DRAFT, "Newer text", null, null, null, null, 0);

        // Act
        SessionRecordResult result = service.updateSessionRecord(command);

        // Assert
        assertThat(result.version()).isOne();
    }

    @Test
    void staleCaseStatusChangeIsRejected() {
        // Arrange
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(clinicalCase()));

        // Act
        Throwable thrown = catchThrowable(() -> service.changeClinicalCaseStatus(
                new ChangeClinicalCaseStatusCommand(PSYCHOLOGIST_ID, CASE_ID, ClinicalCaseStatus.DISCHARGED, 3)));

        // Assert
        assertThat(thrown).isInstanceOf(StaleResourceVersionException.class);
    }

    @Test
    void creatingAPlanThatAlreadyExistsIsRejected() {
        // Arrange
        arrangeExistingPlanWithGoal();
        var command = carePlanCommand(new CareGoalInput(null, "Second plan", null, null, 0));

        // Act
        Throwable thrown = catchThrowable(() -> service.putCarePlan(command));

        // Assert
        assertThat(thrown).isInstanceOf(StaleResourceVersionException.class);
        verify(carePlans, never()).save(any());
    }

    @Test
    void replacingAPlanThatDoesNotExistIsRejected() {
        // Arrange
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(clinicalCase()));
        when(carePlans.findByClinicalCaseId(CASE_ID)).thenReturn(Optional.empty());
        var command = carePlanUpdate(new CareGoalInput(null, "Goal", null, null, 0));

        // Act
        Throwable thrown = catchThrowable(() -> service.putCarePlan(command));

        // Assert
        assertThat(thrown).isInstanceOf(StaleResourceVersionException.class);
    }

    @Test
    void creatingAPracticeProfileThatAlreadyExistsIsRejected() {
        // Arrange
        when(practiceProfiles.findByPsychologistId(PSYCHOLOGIST_ID)).thenReturn(Optional.of(
                stored(PracticeProfile.create(PSYCHOLOGIST_ID, null, ZoneId.of("UTC"), 50, null, false, NOW))));
        var command = new UpdatePracticeProfileCommand(
                PSYCHOLOGIST_ID, "OPP 789", ZoneId.of("Europe/Lisbon"), 45, "Lisbon", true, null);

        // Act
        Throwable thrown = catchThrowable(() -> service.updatePracticeProfile(command));

        // Assert
        assertThat(thrown).isInstanceOf(StaleResourceVersionException.class);
    }

    @Test
    void signsOwnedDraftNoteAndRecordsTheSigner() {
        // Arrange
        SessionRecord session = draftSession(PSYCHOLOGIST_ID, "Summary");
        when(sessionRecords.findById(session.id())).thenReturn(Optional.of(session));
        Instant before = Instant.now();

        // Act
        SessionRecordResult signed = service.signSessionRecord(
                new SignSessionRecordCommand(PSYCHOLOGIST_ID, session.id(), 0));

        // Assert
        assertThat(signed.noteStatus()).isEqualTo(NoteStatus.SIGNED);
        assertThat(signed.signedBy()).isEqualTo(PSYCHOLOGIST_ID.value());
        assertThat(signed.signedAt()).isNotNull().isAfterOrEqualTo(before);
        assertThat(signed.updatedAt()).isEqualTo(signed.signedAt());
    }

    @Test
    void savesTheSignedNote() {
        // Arrange
        SessionRecord session = draftSession(PSYCHOLOGIST_ID, "Summary");
        when(sessionRecords.findById(session.id())).thenReturn(Optional.of(session));

        // Act
        service.signSessionRecord(new SignSessionRecordCommand(PSYCHOLOGIST_ID, session.id(), 0));

        // Assert
        verify(sessionRecords).save(session);
    }

    @Test
    void rejectsSigningMissingNote() {
        // Arrange
        SessionRecordId missingId = new SessionRecordId(UUID.randomUUID());
        when(sessionRecords.findById(missingId)).thenReturn(Optional.empty());

        // Act
        Throwable thrown = catchThrowable(() ->
                service.signSessionRecord(new SignSessionRecordCommand(PSYCHOLOGIST_ID, missingId, 0)));

        // Assert
        assertThat(thrown).isInstanceOf(SessionRecordNotFoundException.class);
    }

    @Test
    void rejectsSigningAnotherPsychologistsNoteWithoutChangingIt() {
        // Arrange
        SessionRecord foreign = draftSession(OTHER_PSYCHOLOGIST, "Summary");
        when(sessionRecords.findById(foreign.id())).thenReturn(Optional.of(foreign));

        // Act
        Throwable thrown = catchThrowable(() ->
                service.signSessionRecord(new SignSessionRecordCommand(PSYCHOLOGIST_ID, foreign.id(), 0)));

        // Assert
        assertThat(thrown).isInstanceOf(SessionRecordNotFoundException.class);
        assertThat(foreign.noteStatus()).isEqualTo(NoteStatus.DRAFT);
        verify(sessionRecords, never()).save(any());
    }

    @Test
    void rejectsSigningAnAlreadySignedNote() {
        // Arrange
        SessionRecord signed = draftSession(PSYCHOLOGIST_ID, "Summary").sign(PSYCHOLOGIST_ID, NOW);
        when(sessionRecords.findById(signed.id())).thenReturn(Optional.of(signed));

        // Act
        Throwable thrown = catchThrowable(() ->
                service.signSessionRecord(new SignSessionRecordCommand(PSYCHOLOGIST_ID, signed.id(), 0)));

        // Assert
        assertThat(thrown).isInstanceOf(SignedSessionRecordException.class);
        verify(sessionRecords, never()).save(any());
    }

    @Test
    void rejectsSigningANoteWithoutSummary() {
        // Arrange
        SessionRecord withoutSummary = draftSession(PSYCHOLOGIST_ID, null);
        when(sessionRecords.findById(withoutSummary.id())).thenReturn(Optional.of(withoutSummary));

        // Act
        Throwable thrown = catchThrowable(() ->
                service.signSessionRecord(new SignSessionRecordCommand(PSYCHOLOGIST_ID, withoutSummary.id(), 0)));

        // Assert
        assertThat(thrown).isInstanceOf(SessionRecordNotSignableException.class);
        verify(sessionRecords, never()).save(any());
    }

    @Test
    void rejectsEditsToAnotherPsychologistsSessionRecord() {
        // Arrange
        SessionRecord foreign = draftSession(OTHER_PSYCHOLOGIST, "Summary");
        when(sessionRecords.findById(foreign.id())).thenReturn(Optional.of(foreign));
        var command = new UpdateSessionRecordCommand(
                PSYCHOLOGIST_ID, foreign.id(), null, NOW.plusSeconds(60), SessionModality.ONLINE, 50,
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, "Rewritten", null, null, null, null, 0);

        // Act
        Throwable thrown = catchThrowable(() -> service.updateSessionRecord(command));

        // Assert
        assertThat(thrown).isInstanceOf(SessionRecordNotFoundException.class);
        assertThat(foreign.summary()).isEqualTo("Summary");
        verify(sessionRecords, never()).save(any());
    }

    @Test
    void rejectsEditsToSignedNotesWithoutSaving() {
        // Arrange
        SessionRecord signed = draftSession(PSYCHOLOGIST_ID, "Summary").sign(PSYCHOLOGIST_ID, NOW);
        when(sessionRecords.findById(signed.id())).thenReturn(Optional.of(signed));
        var command = new UpdateSessionRecordCommand(
                PSYCHOLOGIST_ID, signed.id(), null, NOW.plusSeconds(60), SessionModality.ONLINE, 50,
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, "Rewritten", null, null, null, null, 0);

        // Act
        Throwable thrown = catchThrowable(() -> service.updateSessionRecord(command));

        // Assert
        assertThat(thrown).isInstanceOf(SignedSessionRecordException.class);
        assertThat(signed.noteStatus()).isEqualTo(NoteStatus.SIGNED);
        assertThat(signed.summary()).isEqualTo("Summary");
        verify(sessionRecords, never()).save(any());
    }

    @Test
    void keepsTheIdentityOfGoalsAlreadyInThePlan() {
        // Arrange
        CareGoalId existingGoalId = arrangeExistingPlanWithGoal();
        var command = carePlanUpdate(
                new CareGoalInput(existingGoalId.value(), "Goal", null, GoalStatus.ACHIEVED, 100));

        // Act
        CarePlanResult result = service.putCarePlan(command);

        // Assert
        assertThat(result.goals()).singleElement()
                .satisfies(goal -> assertThat(goal.id()).isEqualTo(existingGoalId.value()));
    }

    @Test
    void generatesServerIdAndDefaultStatusForNewGoals() {
        // Arrange
        CareGoalId existingGoalId = arrangeExistingPlanWithGoal();
        var command = carePlanUpdate(new CareGoalInput(null, "New goal", null, null, 0));

        // Act
        CarePlanResult result = service.putCarePlan(command);

        // Assert
        assertThat(result.goals()).singleElement().satisfies(goal -> {
            assertThat(goal.id()).isNotNull().isNotEqualTo(existingGoalId.value());
            assertThat(goal.status()).isEqualTo(GoalStatus.NOT_STARTED);
        });
    }

    @Test
    void rejectsClientGoalIdsWhenCreatingAPlan() {
        // Arrange
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(clinicalCase()));
        when(carePlans.findByClinicalCaseId(CASE_ID)).thenReturn(Optional.empty());
        var command = carePlanCommand(new CareGoalInput(UUID.randomUUID(), "Hijacked goal", null, null, 0));

        // Act
        Throwable thrown = catchThrowable(() -> service.putCarePlan(command));

        // Assert
        assertThat(thrown).isInstanceOf(ClinicalAccessDeniedException.class);
        verify(carePlans, never()).save(any());
    }

    @Test
    void rejectsGoalIdsThatDoNotBelongToTheExistingPlan() {
        // Arrange
        arrangeExistingPlanWithGoal();
        var command = carePlanUpdate(new CareGoalInput(UUID.randomUUID(), "Hijacked goal", null, null, 0));

        // Act
        Throwable thrown = catchThrowable(() -> service.putCarePlan(command));

        // Assert
        assertThat(thrown).isInstanceOf(ClinicalAccessDeniedException.class);
        verify(carePlans, never()).save(any());
    }

    @Test
    void updatesSessionWithoutCase() {
        // Arrange
        SessionRecord session = stored(SessionRecord.fromCompletedAppointment(new SessionRecordId(UUID.randomUUID()),
                CASE_ID, PSYCHOLOGIST_ID, PATIENT_ID, null, NOW, SessionModality.ONLINE, 50, NOW));
        when(sessionRecords.findById(session.id())).thenReturn(Optional.of(session));
        var command = new UpdateSessionRecordCommand(
                PSYCHOLOGIST_ID, session.id(), null, NOW.plusSeconds(60), SessionModality.ONLINE, 50,
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, null, null, null, null, null, 0);

        // Act
        SessionRecordResult updated = service.updateSessionRecord(command);

        // Assert
        assertThat(updated.clinicalCaseId()).isNull();
    }

    @Test
    void rejectsLinkingSessionToAnotherPatientsCase() {
        // Arrange
        SessionRecord session = stored(SessionRecord.fromCompletedAppointment(new SessionRecordId(UUID.randomUUID()),
                CASE_ID, PSYCHOLOGIST_ID, PATIENT_ID, null, NOW, SessionModality.ONLINE, 50, NOW));
        when(sessionRecords.findById(session.id())).thenReturn(Optional.of(session));
        ClinicalCase otherPatientCase = ClinicalCase.rehydrate(CASE_ID, PSYCHOLOGIST_ID,
                new PatientId(UUID.randomUUID()), "Concern", ClinicalCaseStatus.ACTIVE, NOW, null, NOW, NOW, 0L);
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(otherPatientCase));
        var command = new UpdateSessionRecordCommand(
                PSYCHOLOGIST_ID, session.id(), CASE_ID, NOW.plusSeconds(120), SessionModality.ONLINE, 50,
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, null, null, null, null, null, 0);

        // Act
        Throwable thrown = catchThrowable(() -> service.updateSessionRecord(command));

        // Assert
        assertThat(thrown).isInstanceOf(ClinicalAccessDeniedException.class);
    }

    private CareGoalId arrangeExistingPlanWithGoal() {
        ClinicalCase owned = clinicalCase();
        CareGoalId existingGoalId = new CareGoalId(UUID.randomUUID());
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(owned));
        when(carePlans.findByClinicalCaseId(CASE_ID)).thenReturn(Optional.of(carePlan(owned, existingGoalId)));
        return existingGoalId;
    }

    // Creates a plan: no version is expected.
    private PutCarePlanCommand carePlanCommand(CareGoalInput goal) {
        return new PutCarePlanCommand(PSYCHOLOGIST_ID, CASE_ID, "Reduce avoidance", "Weekly", REVIEW_DATE,
                List.of(goal), null);
    }

    // Replaces the stored plan at version 0.
    private PutCarePlanCommand carePlanUpdate(CareGoalInput goal) {
        return new PutCarePlanCommand(PSYCHOLOGIST_ID, CASE_ID, "Reduce avoidance", "Weekly", REVIEW_DATE,
                List.of(goal), 0L);
    }

    private CarePlan carePlan(ClinicalCase clinicalCase, CareGoalId goalId) {
        return stored(CarePlan.create(new CarePlanId(UUID.randomUUID()), clinicalCase, "Focus", "Weekly", REVIEW_DATE,
                List.of(new CareGoal(goalId, "Goal", null, GoalStatus.IN_PROGRESS, 10)), NOW));
    }

    static Stream<Arguments> claimsOfAnotherPsychologistOrPatient() {
        return Stream.of(
                Arguments.of("another psychologist", OTHER_PSYCHOLOGIST, PATIENT_ID),
                Arguments.of("another patient of the same psychologist", PSYCHOLOGIST_ID, OTHER_PATIENT),
                Arguments.of("another psychologist and patient", OTHER_PSYCHOLOGIST, OTHER_PATIENT));
    }

    private void arrangeCompletedAppointmentClaimedBy(SessionRecord claimed) {
        when(sessionRecords.findByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.of(claimed));
        when(clinicalCases.findOpenByPsychologistIdAndPatientId(PSYCHOLOGIST_ID, PATIENT_ID))
                .thenReturn(Optional.empty());
        when(practiceProfiles.findByPsychologistId(PSYCHOLOGIST_ID)).thenReturn(Optional.empty());
    }

    private void arrangeVerifiedLinkClaimedBy(SessionRecord claimed) {
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(clinicalCase()));
        when(appointmentDirectory.findPatientOfOwnedAppointment(APPOINTMENT_ID, PSYCHOLOGIST_ID))
                .thenReturn(Optional.of(PATIENT_ID));
        when(sessionRecords.findByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.of(claimed));
    }

    // A record linked before ADR-021 to an appointment that is not its own; signed, so the note itself is final.
    private SessionRecord claimingRecord(PsychologistId psychologist, PatientId patient) {
        return SessionRecord.rehydrate(new SessionRecordId(UUID.randomUUID()), null, psychologist, patient,
                APPOINTMENT_ID, NOW, SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED, NoteStatus.SIGNED,
                "Signed summary", null, null, null, null, NOW, NOW, NOW, psychologist, 0L);
    }

    private CreateSessionRecordCommand sessionCommand(AppointmentId appointmentId) {
        return new CreateSessionRecordCommand(PSYCHOLOGIST_ID, CASE_ID, appointmentId, NOW, SessionModality.ONLINE,
                50, AttendanceStatus.ATTENDED, "Summary", "Focus", "CBT", "Homework", "Next");
    }

    private ListAppender<ILoggingEvent> captureServiceLogs() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(ClinicalService.class)).addAppender(appender);
        return appender;
    }

    private void releaseServiceLogs(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(ClinicalService.class)).detachAppender(appender);
        appender.stop();
    }

    private ProcessAppointmentCompletedCommand appointmentCompleted() {
        return new ProcessAppointmentCompletedCommand(new DomainEventId(UUID.randomUUID()), APPOINTMENT_ID,
                PATIENT_ID, PSYCHOLOGIST_ID, NOW, SessionModality.ONLINE);
    }

    private SessionRecord draftSession(PsychologistId owner, String summary) {
        return SessionRecord.rehydrate(new SessionRecordId(UUID.randomUUID()), CASE_ID, owner, PATIENT_ID, null,
                NOW, SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED, NoteStatus.DRAFT, summary, null, null,
                null, null, NOW, NOW, null, null, 0L);
    }

    // The stored copy of a record: unsaved records get version 0, stored ones their next version.
    private static SessionRecord stored(SessionRecord session) {
        return SessionRecord.rehydrate(session.id(), session.clinicalCaseId().orElse(null), session.psychologistId(),
                session.patientId(), session.appointmentId().orElse(null), session.sessionDate(), session.modality(),
                session.durationMinutes(), session.attendanceStatus(), session.noteStatus(), session.summary(),
                session.focusAreas(), session.interventions(), session.homework(), session.nextSteps(),
                session.createdAt(), session.updatedAt(), session.signedAt().orElse(null),
                session.signedBy().orElse(null), nextVersion(session.version()));
    }

    private static ClinicalCase stored(ClinicalCase clinicalCase) {
        return ClinicalCase.rehydrate(clinicalCase.id(), clinicalCase.psychologistId(), clinicalCase.patientId(),
                clinicalCase.presentingConcern(), clinicalCase.status(), clinicalCase.openedAt(),
                clinicalCase.closedAt(), clinicalCase.createdAt(), clinicalCase.updatedAt(),
                nextVersion(clinicalCase.version()));
    }

    private static CarePlan stored(CarePlan carePlan) {
        return CarePlan.rehydrate(carePlan.id(), carePlan.clinicalCaseId(), carePlan.psychologistId(),
                carePlan.patientId(), carePlan.therapeuticFocus(), carePlan.plannedFrequency(), carePlan.reviewDate(),
                carePlan.goals(), carePlan.createdAt(), carePlan.updatedAt(), nextVersion(carePlan.version()));
    }

    private static PracticeProfile stored(PracticeProfile profile) {
        return PracticeProfile.rehydrate(profile.psychologistId(), profile.professionalRegistration(),
                profile.timezone(), profile.defaultSessionDurationMinutes(), profile.primaryLocation(),
                profile.telehealthEnabled(), profile.createdAt(), profile.updatedAt(), nextVersion(profile.version()));
    }

    private static long nextVersion(Optional<Long> version) {
        return version.map(current -> current + 1).orElse(0L);
    }

    private ClinicalCase clinicalCase() {
        return ClinicalCase.rehydrate(CASE_ID, PSYCHOLOGIST_ID, PATIENT_ID, "Anxiety symptoms",
                ClinicalCaseStatus.ACTIVE, NOW, null, NOW, NOW, 0L);
    }
}
