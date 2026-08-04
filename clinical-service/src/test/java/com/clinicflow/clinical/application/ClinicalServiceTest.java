package com.clinicflow.clinical.application;

import com.clinicflow.clinical.application.commands.CareGoalInput;
import com.clinicflow.clinical.application.commands.CreateClinicalCaseCommand;
import com.clinicflow.clinical.application.commands.ProcessAppointmentCompletedCommand;
import com.clinicflow.clinical.application.commands.PutCarePlanCommand;
import com.clinicflow.clinical.application.commands.ChangeClinicalCaseStatusCommand;
import com.clinicflow.clinical.application.commands.CreateSessionRecordCommand;
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
import com.clinicflow.clinical.domain.AppointmentId;
import com.clinicflow.clinical.domain.AttendanceStatus;
import com.clinicflow.clinical.domain.CarePlan;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicalServiceTest {
    private static final PsychologistId PSYCHOLOGIST_ID =
            new PsychologistId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private static final PatientId PATIENT_ID =
            new PatientId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    private static final ClinicalCaseId CASE_ID =
            new ClinicalCaseId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

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
    void createsCaseUnlessOpenCaseExists() {
        when(clinicalCases.existsOpenByPsychologistIdAndPatientId(PSYCHOLOGIST_ID, PATIENT_ID)).thenReturn(false);

        var result = service.createClinicalCase(new CreateClinicalCaseCommand(
                PSYCHOLOGIST_ID, PATIENT_ID, "Anxiety symptoms"));

        ArgumentCaptor<ClinicalCase> captor = ArgumentCaptor.forClass(ClinicalCase.class);
        verify(clinicalCases).save(captor.capture());
        assertThat(result.id()).isEqualTo(captor.getValue().id().value());
        assertThat(result.patientId()).isEqualTo(PATIENT_ID.value());

        when(clinicalCases.existsOpenByPsychologistIdAndPatientId(PSYCHOLOGIST_ID, PATIENT_ID)).thenReturn(true);
        assertThatThrownBy(() -> service.createClinicalCase(new CreateClinicalCaseCommand(
                PSYCHOLOGIST_ID, PATIENT_ID, "Another concern")))
                .isInstanceOf(ActiveClinicalCaseExistsException.class);
    }

    @Test
    void createsCarePlanForOwnedCase() {
        ClinicalCase clinicalCase = clinicalCase();
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(clinicalCase));
        when(carePlans.findByClinicalCaseId(CASE_ID)).thenReturn(Optional.empty());

        var result = service.putCarePlan(new PutCarePlanCommand(PSYCHOLOGIST_ID, CASE_ID,
                "Reduce avoidance", "Weekly", LocalDate.of(2026, 7, 1),
                List.of(new CareGoalInput(null, "Practice exposure", null, GoalStatus.IN_PROGRESS, 20))));

        ArgumentCaptor<CarePlan> captor = ArgumentCaptor.forClass(CarePlan.class);
        verify(carePlans).save(captor.capture());
        assertThat(result.goals()).singleElement().satisfies(goal -> {
            assertThat(goal.description()).isEqualTo("Practice exposure");
            assertThat(goal.progressPercentage()).isEqualTo(20);
        });
        assertThat(captor.getValue().clinicalCaseId()).isEqualTo(CASE_ID);
    }

    @Test
    void completedAppointmentCreatesLinkedSessionOnlyOnce() {
        AppointmentId appointmentId = new AppointmentId(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"));
        when(sessionRecords.findByAppointmentId(appointmentId)).thenReturn(Optional.empty());
        when(clinicalCases.findOpenByPsychologistIdAndPatientId(PSYCHOLOGIST_ID, PATIENT_ID))
                .thenReturn(Optional.of(clinicalCase()));
        when(practiceProfiles.findByPsychologistId(PSYCHOLOGIST_ID)).thenReturn(Optional.of(
                PracticeProfile.create(PSYCHOLOGIST_ID, null, ZoneId.of("UTC"), 60, null, true, NOW)));

        service.processAppointmentCompleted(new ProcessAppointmentCompletedCommand(
                new DomainEventId(UUID.randomUUID()), appointmentId, PATIENT_ID, PSYCHOLOGIST_ID,
                NOW, SessionModality.ONLINE));

        ArgumentCaptor<SessionRecord> captor = ArgumentCaptor.forClass(SessionRecord.class);
        verify(sessionRecords).save(captor.capture());
        assertThat(captor.getValue().clinicalCaseId()).contains(CASE_ID);
        assertThat(captor.getValue().durationMinutes()).isEqualTo(60);

        when(sessionRecords.findByAppointmentId(appointmentId)).thenReturn(Optional.of(captor.getValue()));
        service.processAppointmentCompleted(new ProcessAppointmentCompletedCommand(
                new DomainEventId(UUID.randomUUID()), appointmentId, PATIENT_ID, PSYCHOLOGIST_ID,
                NOW, SessionModality.ONLINE));
        verify(sessionRecords).save(captor.getValue());
    }

    @Test
    void assemblesPatientHistoryFromOwnedRecords() {
        ClinicalCase clinicalCase = clinicalCase();
        when(clinicalCases.findByPsychologistIdAndPatientId(PSYCHOLOGIST_ID, PATIENT_ID))
                .thenReturn(List.of(clinicalCase));
        when(carePlans.findByClinicalCaseId(CASE_ID)).thenReturn(Optional.empty());
        when(sessionRecords.findByPsychologistIdAndPatientId(PSYCHOLOGIST_ID, PATIENT_ID))
                .thenReturn(List.of(SessionRecord.fromCompletedAppointment(
                        new com.clinicflow.clinical.domain.SessionRecordId(UUID.randomUUID()), CASE_ID,
                        PSYCHOLOGIST_ID, PATIENT_ID, new AppointmentId(UUID.randomUUID()),
                        NOW, SessionModality.IN_PERSON, 50, NOW)));

        var result = service.getPatientClinicalHistory(new GetPatientClinicalHistoryQuery(PSYCHOLOGIST_ID, PATIENT_ID));

        assertThat(result.cases()).hasSize(1);
        assertThat(result.sessionRecords()).hasSize(1);
    }

    @Test
    void readsCreatesAndUpdatesPracticeProfile() {
        var fallback = service.getPracticeProfile(new GetPracticeProfileQuery(PSYCHOLOGIST_ID));
        assertThat(fallback.timezone()).isEqualTo("UTC");
        assertThat(fallback.defaultSessionDurationMinutes()).isEqualTo(50);

        var created = service.updatePracticeProfile(new UpdatePracticeProfileCommand(
                PSYCHOLOGIST_ID, "OPP 123", ZoneId.of("Europe/Lisbon"), 45, "Lisbon", true));
        assertThat(created.professionalRegistration()).isEqualTo("OPP 123");
        verify(practiceProfiles).save(org.mockito.ArgumentMatchers.any(PracticeProfile.class));

        PracticeProfile existing = PracticeProfile.create(
                PSYCHOLOGIST_ID, null, ZoneId.of("UTC"), 50, null, false, NOW);
        when(practiceProfiles.findByPsychologistId(PSYCHOLOGIST_ID)).thenReturn(Optional.of(existing));
        var updated = service.updatePracticeProfile(new UpdatePracticeProfileCommand(
                PSYCHOLOGIST_ID, "OPP 456", ZoneId.of("Europe/Lisbon"), 60, "Porto", true));
        assertThat(updated.defaultSessionDurationMinutes()).isEqualTo(60);
        assertThat(updated.primaryLocation()).isEqualTo("Porto");
    }

    @Test
    void listsGetsChangesAndProtectsClinicalCases() {
        ClinicalCase owned = clinicalCase();
        when(clinicalCases.findByPsychologistId(PSYCHOLOGIST_ID)).thenReturn(List.of(owned));
        when(clinicalCases.findByPsychologistIdAndStatus(PSYCHOLOGIST_ID, ClinicalCaseStatus.ACTIVE))
                .thenReturn(List.of(owned));
        when(clinicalCases.findByPsychologistIdAndPatientId(PSYCHOLOGIST_ID, PATIENT_ID))
                .thenReturn(List.of(owned));
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(owned));

        assertThat(service.listClinicalCases(new ListClinicalCasesQuery(PSYCHOLOGIST_ID, null, null))).hasSize(1);
        assertThat(service.listClinicalCases(new ListClinicalCasesQuery(
                PSYCHOLOGIST_ID, null, ClinicalCaseStatus.ACTIVE))).hasSize(1);
        assertThat(service.listClinicalCases(new ListClinicalCasesQuery(
                PSYCHOLOGIST_ID, PATIENT_ID, null))).hasSize(1);
        assertThat(service.listClinicalCases(new ListClinicalCasesQuery(
                PSYCHOLOGIST_ID, PATIENT_ID, ClinicalCaseStatus.ACTIVE))).hasSize(1);
        assertThat(service.getClinicalCase(new GetClinicalCaseQuery(PSYCHOLOGIST_ID, CASE_ID)).id())
                .isEqualTo(CASE_ID.value());

        var discharged = service.changeClinicalCaseStatus(new ChangeClinicalCaseStatusCommand(
                PSYCHOLOGIST_ID, CASE_ID, ClinicalCaseStatus.DISCHARGED));
        assertThat(discharged.status()).isEqualTo(ClinicalCaseStatus.DISCHARGED);
        verify(clinicalCases).save(owned);

        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getClinicalCase(new GetClinicalCaseQuery(PSYCHOLOGIST_ID, CASE_ID)))
                .isInstanceOf(ClinicalCaseNotFoundException.class);
    }

    @Test
    void getsCarePlanAndReportsMissingPlan() {
        ClinicalCase owned = clinicalCase();
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(owned));
        CarePlan plan = CarePlan.create(new com.clinicflow.clinical.domain.CarePlanId(UUID.randomUUID()), owned,
                "Focus", "Weekly", LocalDate.now().plusMonths(1),
                List.of(new com.clinicflow.clinical.domain.CareGoal(
                        new com.clinicflow.clinical.domain.CareGoalId(UUID.randomUUID()), "Goal", null,
                        GoalStatus.NOT_STARTED, 0)), NOW);
        when(carePlans.findByClinicalCaseId(CASE_ID)).thenReturn(Optional.of(plan));

        assertThat(service.getCarePlan(new GetCarePlanQuery(PSYCHOLOGIST_ID, CASE_ID)).therapeuticFocus())
                .isEqualTo("Focus");
        when(carePlans.findByClinicalCaseId(CASE_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getCarePlan(new GetCarePlanQuery(PSYCHOLOGIST_ID, CASE_ID)))
                .isInstanceOf(CarePlanNotFoundException.class);
    }

    @Test
    void createsListsUpdatesAndProtectsSessionRecords() {
        ClinicalCase owned = clinicalCase();
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(owned));
        var created = service.createSessionRecord(new CreateSessionRecordCommand(
                PSYCHOLOGIST_ID, CASE_ID, null, NOW, SessionModality.ONLINE, 50,
                AttendanceStatus.ATTENDED, "Summary", "Focus", "CBT", "Homework", "Next"));
        assertThat(created.summary()).isEqualTo("Summary");

        ArgumentCaptor<SessionRecord> captor = ArgumentCaptor.forClass(SessionRecord.class);
        verify(sessionRecords).save(captor.capture());
        SessionRecord session = captor.getValue();
        when(sessionRecords.findByClinicalCaseId(CASE_ID)).thenReturn(List.of(session));
        assertThat(service.listSessionRecords(new ListSessionRecordsQuery(PSYCHOLOGIST_ID, CASE_ID))).hasSize(1);

        when(sessionRecords.findById(session.id())).thenReturn(Optional.of(session));
        var updated = service.updateSessionRecord(new UpdateSessionRecordCommand(
                PSYCHOLOGIST_ID, session.id(), CASE_ID, NOW.plusSeconds(60), SessionModality.IN_PERSON, 55,
                AttendanceStatus.ATTENDED, NoteStatus.SIGNED, "Updated", null, null, null, null));
        assertThat(updated.noteStatus()).isEqualTo(NoteStatus.SIGNED);
        assertThat(updated.durationMinutes()).isEqualTo(55);

        SessionRecordId missingId = new SessionRecordId(UUID.randomUUID());
        when(sessionRecords.findById(missingId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateSessionRecord(new UpdateSessionRecordCommand(
                PSYCHOLOGIST_ID, missingId, null, NOW, SessionModality.ONLINE, 50,
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, null, null, null, null, null)))
                .isInstanceOf(SessionRecordNotFoundException.class);

        PsychologistId other = new PsychologistId(UUID.randomUUID());
        assertThatThrownBy(() -> service.getClinicalCase(new GetClinicalCaseQuery(other, CASE_ID)))
                .isInstanceOf(ClinicalAccessDeniedException.class);
    }

    @Test
    void mapsExplicitGoalIdentityAndDefaultStatus() {
        ClinicalCase clinicalCase = clinicalCase();
        UUID goalId = UUID.randomUUID();
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(clinicalCase));
        when(carePlans.findByClinicalCaseId(CASE_ID)).thenReturn(Optional.empty());

        var result = service.putCarePlan(new PutCarePlanCommand(PSYCHOLOGIST_ID, CASE_ID,
                "Focus", "Weekly", LocalDate.of(2026, 7, 1),
                List.of(new CareGoalInput(goalId, "Goal", null, null, 0))));

        assertThat(result.goals()).singleElement().satisfies(goal -> {
            assertThat(goal.id()).isEqualTo(goalId);
            assertThat(goal.status()).isEqualTo(GoalStatus.NOT_STARTED);
        });
    }

    @Test
    void updatesSessionWithoutCaseAndRejectsCaseForAnotherPatient() {
        SessionRecord session = SessionRecord.fromCompletedAppointment(new SessionRecordId(UUID.randomUUID()),
                CASE_ID, PSYCHOLOGIST_ID, PATIENT_ID, null, NOW, SessionModality.ONLINE, 50, NOW);
        when(sessionRecords.findById(session.id())).thenReturn(Optional.of(session));

        var updated = service.updateSessionRecord(new UpdateSessionRecordCommand(
                PSYCHOLOGIST_ID, session.id(), null, NOW.plusSeconds(60), SessionModality.ONLINE, 50,
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, null, null, null, null, null));
        assertThat(updated.clinicalCaseId()).isNull();

        PatientId otherPatient = new PatientId(UUID.randomUUID());
        ClinicalCase otherPatientCase = ClinicalCase.rehydrate(CASE_ID, PSYCHOLOGIST_ID, otherPatient,
                "Concern", ClinicalCaseStatus.ACTIVE, NOW, null, NOW, NOW);
        when(clinicalCases.findById(CASE_ID)).thenReturn(Optional.of(otherPatientCase));
        assertThatThrownBy(() -> service.updateSessionRecord(new UpdateSessionRecordCommand(
                PSYCHOLOGIST_ID, session.id(), CASE_ID, NOW.plusSeconds(120), SessionModality.ONLINE, 50,
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, null, null, null, null, null)))
                .isInstanceOf(ClinicalAccessDeniedException.class);
    }

    private ClinicalCase clinicalCase() {
        return ClinicalCase.rehydrate(CASE_ID, PSYCHOLOGIST_ID, PATIENT_ID, "Anxiety symptoms",
                com.clinicflow.clinical.domain.ClinicalCaseStatus.ACTIVE, NOW, null, NOW, NOW);
    }
}
