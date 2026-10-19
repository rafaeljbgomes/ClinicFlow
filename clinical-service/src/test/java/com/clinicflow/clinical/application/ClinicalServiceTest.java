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
import com.clinicflow.clinical.application.exceptions.CarePlanNotFoundException;
import com.clinicflow.clinical.application.exceptions.ClinicalAccessDeniedException;
import com.clinicflow.clinical.application.exceptions.ClinicalCaseNotFoundException;
import com.clinicflow.clinical.application.exceptions.SessionRecordNotFoundException;
import com.clinicflow.clinical.application.mapping.ClinicalApplicationMapper;
import com.clinicflow.clinical.application.ports.CarePlanRepository;
import com.clinicflow.clinical.application.ports.ClinicalCaseRepository;
import com.clinicflow.clinical.application.ports.PracticeProfileRepository;
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
import com.clinicflow.clinical.domain.NoteStatus;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PracticeProfile;
import com.clinicflow.clinical.domain.PsychologistId;
import com.clinicflow.clinical.domain.SessionModality;
import com.clinicflow.clinical.domain.SessionRecord;
import com.clinicflow.clinical.domain.SessionRecordId;
import com.clinicflow.clinical.domain.SessionRecordNotSignableException;
import com.clinicflow.clinical.domain.SignedSessionRecordException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicalServiceTest {
    private static final PsychologistId PSYCHOLOGIST_ID =
            new PsychologistId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private static final PsychologistId OTHER_PSYCHOLOGIST = new PsychologistId(UUID.randomUUID());
    private static final PatientId PATIENT_ID =
            new PatientId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
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

    private ClinicalService service;

    @BeforeEach
    void setUp() {
        service = new ClinicalService(practiceProfiles, clinicalCases, carePlans, sessionRecords,
                Mappers.getMapper(ClinicalApplicationMapper.class));
    }

    @Test
    void createsCaseWhenNoOpenCaseExists() {
        // Arrange
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
        when(clinicalCases.existsOpenByPsychologistIdAndPatientId(PSYCHOLOGIST_ID, PATIENT_ID)).thenReturn(true);
        var command = new CreateClinicalCaseCommand(PSYCHOLOGIST_ID, PATIENT_ID, "Another concern");

        // Act
        Throwable thrown = catchThrowable(() -> service.createClinicalCase(command));

        // Assert
        assertThat(thrown).isInstanceOf(ActiveClinicalCaseExistsException.class);
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
                PracticeProfile.create(PSYCHOLOGIST_ID, null, ZoneId.of("UTC"), 60, null, true, NOW)));

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
        SessionRecord existing = SessionRecord.fromCompletedAppointment(new SessionRecordId(UUID.randomUUID()),
                CASE_ID, PSYCHOLOGIST_ID, PATIENT_ID, APPOINTMENT_ID, NOW, SessionModality.ONLINE, 60, NOW);
        when(sessionRecords.findByAppointmentId(APPOINTMENT_ID)).thenReturn(Optional.of(existing));

        // Act
        service.processAppointmentCompleted(appointmentCompleted());

        // Assert
        verify(sessionRecords, never()).save(any());
    }

    @Test
    void assemblesPatientHistoryFromOwnedRecords() {
        // Arrange
        when(clinicalCases.findByPsychologistIdAndPatientId(PSYCHOLOGIST_ID, PATIENT_ID))
                .thenReturn(List.of(clinicalCase()));
        when(carePlans.findByClinicalCaseId(CASE_ID)).thenReturn(Optional.empty());
        when(sessionRecords.findByPsychologistIdAndPatientId(PSYCHOLOGIST_ID, PATIENT_ID))
                .thenReturn(List.of(SessionRecord.fromCompletedAppointment(
                        new SessionRecordId(UUID.randomUUID()), CASE_ID,
                        PSYCHOLOGIST_ID, PATIENT_ID, new AppointmentId(UUID.randomUUID()),
                        NOW, SessionModality.IN_PERSON, 50, NOW)));
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
                PSYCHOLOGIST_ID, "OPP 123", ZoneId.of("Europe/Lisbon"), 45, "Lisbon", true);

        // Act
        PracticeProfileResult created = service.updatePracticeProfile(command);

        // Assert
        assertThat(created.professionalRegistration()).isEqualTo("OPP 123");
        verify(practiceProfiles).save(any(PracticeProfile.class));
    }

    @Test
    void updatesStoredPracticeProfile() {
        // Arrange
        PracticeProfile existing = PracticeProfile.create(
                PSYCHOLOGIST_ID, null, ZoneId.of("UTC"), 50, null, false, NOW);
        when(practiceProfiles.findByPsychologistId(PSYCHOLOGIST_ID)).thenReturn(Optional.of(existing));
        var command = new UpdatePracticeProfileCommand(
                PSYCHOLOGIST_ID, "OPP 456", ZoneId.of("Europe/Lisbon"), 60, "Porto", true);

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
    void deniesAccessToAnotherPsychologistsCase() {
        // Arrange
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(clinicalCase()));
        var query = new GetClinicalCaseQuery(OTHER_PSYCHOLOGIST, CASE_ID);

        // Act
        Throwable thrown = catchThrowable(() -> service.getClinicalCase(query));

        // Assert
        assertThat(thrown).isInstanceOf(ClinicalAccessDeniedException.class);
    }

    @Test
    void changesStatusOfOwnedCaseAndSavesIt() {
        // Arrange
        ClinicalCase owned = clinicalCase();
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(owned));
        var command = new ChangeClinicalCaseStatusCommand(PSYCHOLOGIST_ID, CASE_ID, ClinicalCaseStatus.DISCHARGED);

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
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, "Updated", null, null, null, null);

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
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, null, null, null, null, null);

        // Act
        Throwable thrown = catchThrowable(() -> service.updateSessionRecord(command));

        // Assert
        assertThat(thrown).isInstanceOf(SessionRecordNotFoundException.class);
    }

    @Test
    void signsOwnedDraftNoteAndRecordsTheSigner() {
        // Arrange
        SessionRecord session = draftSession(PSYCHOLOGIST_ID, "Summary");
        when(sessionRecords.findById(session.id())).thenReturn(Optional.of(session));
        Instant before = Instant.now();

        // Act
        SessionRecordResult signed = service.signSessionRecord(
                new SignSessionRecordCommand(PSYCHOLOGIST_ID, session.id()));

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
        service.signSessionRecord(new SignSessionRecordCommand(PSYCHOLOGIST_ID, session.id()));

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
                service.signSessionRecord(new SignSessionRecordCommand(PSYCHOLOGIST_ID, missingId)));

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
                service.signSessionRecord(new SignSessionRecordCommand(PSYCHOLOGIST_ID, foreign.id())));

        // Assert
        assertThat(thrown).isInstanceOf(ClinicalAccessDeniedException.class);
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
                service.signSessionRecord(new SignSessionRecordCommand(PSYCHOLOGIST_ID, signed.id())));

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
                service.signSessionRecord(new SignSessionRecordCommand(PSYCHOLOGIST_ID, withoutSummary.id())));

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
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, "Rewritten", null, null, null, null);

        // Act
        Throwable thrown = catchThrowable(() -> service.updateSessionRecord(command));

        // Assert
        assertThat(thrown).isInstanceOf(ClinicalAccessDeniedException.class);
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
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, "Rewritten", null, null, null, null);

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
        var command = carePlanCommand(
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
        var command = carePlanCommand(new CareGoalInput(null, "New goal", null, null, 0));

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
        var command = carePlanCommand(new CareGoalInput(UUID.randomUUID(), "Hijacked goal", null, null, 0));

        // Act
        Throwable thrown = catchThrowable(() -> service.putCarePlan(command));

        // Assert
        assertThat(thrown).isInstanceOf(ClinicalAccessDeniedException.class);
        verify(carePlans, never()).save(any());
    }

    @Test
    void updatesSessionWithoutCase() {
        // Arrange
        SessionRecord session = SessionRecord.fromCompletedAppointment(new SessionRecordId(UUID.randomUUID()),
                CASE_ID, PSYCHOLOGIST_ID, PATIENT_ID, null, NOW, SessionModality.ONLINE, 50, NOW);
        when(sessionRecords.findById(session.id())).thenReturn(Optional.of(session));
        var command = new UpdateSessionRecordCommand(
                PSYCHOLOGIST_ID, session.id(), null, NOW.plusSeconds(60), SessionModality.ONLINE, 50,
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, null, null, null, null, null);

        // Act
        SessionRecordResult updated = service.updateSessionRecord(command);

        // Assert
        assertThat(updated.clinicalCaseId()).isNull();
    }

    @Test
    void rejectsLinkingSessionToAnotherPatientsCase() {
        // Arrange
        SessionRecord session = SessionRecord.fromCompletedAppointment(new SessionRecordId(UUID.randomUUID()),
                CASE_ID, PSYCHOLOGIST_ID, PATIENT_ID, null, NOW, SessionModality.ONLINE, 50, NOW);
        when(sessionRecords.findById(session.id())).thenReturn(Optional.of(session));
        ClinicalCase otherPatientCase = ClinicalCase.rehydrate(CASE_ID, PSYCHOLOGIST_ID,
                new PatientId(UUID.randomUUID()), "Concern", ClinicalCaseStatus.ACTIVE, NOW, null, NOW, NOW);
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(otherPatientCase));
        var command = new UpdateSessionRecordCommand(
                PSYCHOLOGIST_ID, session.id(), CASE_ID, NOW.plusSeconds(120), SessionModality.ONLINE, 50,
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, null, null, null, null, null);

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

    private PutCarePlanCommand carePlanCommand(CareGoalInput goal) {
        return new PutCarePlanCommand(PSYCHOLOGIST_ID, CASE_ID, "Reduce avoidance", "Weekly", REVIEW_DATE,
                List.of(goal));
    }

    private CarePlan carePlan(ClinicalCase clinicalCase, CareGoalId goalId) {
        return CarePlan.create(new CarePlanId(UUID.randomUUID()), clinicalCase, "Focus", "Weekly", REVIEW_DATE,
                List.of(new CareGoal(goalId, "Goal", null, GoalStatus.IN_PROGRESS, 10)), NOW);
    }

    private ProcessAppointmentCompletedCommand appointmentCompleted() {
        return new ProcessAppointmentCompletedCommand(new DomainEventId(UUID.randomUUID()), APPOINTMENT_ID,
                PATIENT_ID, PSYCHOLOGIST_ID, NOW, SessionModality.ONLINE);
    }

    private SessionRecord draftSession(PsychologistId owner, String summary) {
        return SessionRecord.rehydrate(new SessionRecordId(UUID.randomUUID()), CASE_ID, owner, PATIENT_ID, null,
                NOW, SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED, NoteStatus.DRAFT, summary, null, null,
                null, null, NOW, NOW, null, null);
    }

    private ClinicalCase clinicalCase() {
        return ClinicalCase.rehydrate(CASE_ID, PSYCHOLOGIST_ID, PATIENT_ID, "Anxiety symptoms",
                ClinicalCaseStatus.ACTIVE, NOW, null, NOW, NOW);
    }
}
