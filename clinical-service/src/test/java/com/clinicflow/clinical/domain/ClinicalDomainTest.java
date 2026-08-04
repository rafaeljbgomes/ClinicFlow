package com.clinicflow.clinical.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClinicalDomainTest {
    private static final PsychologistId PSYCHOLOGIST_ID =
            new PsychologistId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private static final PatientId PATIENT_ID =
            new PatientId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

    @Test
    void practiceProfileValidatesAndMutatesSameInstance() {
        PracticeProfile profile = PracticeProfile.create(PSYCHOLOGIST_ID, "OPP 123",
                ZoneId.of("Europe/Lisbon"), 50, "Lisbon", true, NOW);

        PracticeProfile changed = profile.update("OPP 456", ZoneId.of("UTC"), 60, null, false,
                NOW.plusSeconds(60));

        assertThat(changed).isSameAs(profile);
        assertThat(profile.professionalRegistration()).isEqualTo("OPP 456");
        assertThat(profile.primaryLocation()).isNull();
        assertThatThrownBy(() -> profile.update("x", ZoneId.of("UTC"), 10, null, false, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clinicalCaseTracksLifecycle() {
        ClinicalCase clinicalCase = clinicalCase();

        clinicalCase.changeStatus(ClinicalCaseStatus.ACTIVE, NOW.plusSeconds(60));
        assertThat(clinicalCase.status()).isEqualTo(ClinicalCaseStatus.ACTIVE);
        assertThat(clinicalCase.closedAt()).isNull();

        clinicalCase.changeStatus(ClinicalCaseStatus.DISCHARGED, NOW.plusSeconds(120));
        assertThat(clinicalCase.closedAt()).isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    void carePlanRequiresGoalsAndValidProgress() {
        ClinicalCase clinicalCase = clinicalCase();
        CareGoal goal = new CareGoal(new CareGoalId(UUID.randomUUID()), "Sleep routine",
                LocalDate.of(2026, 7, 1), GoalStatus.IN_PROGRESS, 40);

        CarePlan plan = CarePlan.create(new CarePlanId(UUID.randomUUID()), clinicalCase,
                "Reduce avoidance", "Weekly", LocalDate.of(2026, 7, 15), List.of(goal), NOW);

        assertThat(plan.goals()).containsExactly(goal);
        assertThatThrownBy(() -> new CareGoal(new CareGoalId(UUID.randomUUID()), "Bad",
                null, GoalStatus.IN_PROGRESS, 101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CarePlan.create(new CarePlanId(UUID.randomUUID()), clinicalCase,
                "Focus", "Weekly", LocalDate.now(), List.of(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sessionRecordSupportsAppointmentShellAndSignedUpdate() {
        SessionRecord session = SessionRecord.fromCompletedAppointment(new SessionRecordId(UUID.randomUUID()),
                clinicalCase().id(), PSYCHOLOGIST_ID, PATIENT_ID, new AppointmentId(UUID.randomUUID()),
                NOW, SessionModality.ONLINE, 50, NOW);

        assertThat(session.noteStatus()).isEqualTo(NoteStatus.PENDING_NOTE);

        session.update(session.clinicalCaseId().orElseThrow(), NOW.plusSeconds(30), SessionModality.ONLINE,
                55, AttendanceStatus.ATTENDED, NoteStatus.SIGNED, "Summary", "Focus",
                "Intervention", "Homework", "Next", NOW.plusSeconds(60));

        assertThat(session.noteStatus()).isEqualTo(NoteStatus.SIGNED);
        assertThat(session.summary()).isEqualTo("Summary");
        assertThatThrownBy(() -> session.update(null, NOW, SessionModality.ONLINE, 500,
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, null, null, null, null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exposesCompleteAggregateStateAndUsesIdentityEquality() {
        ClinicalCase clinicalCase = clinicalCase();
        ClinicalCase sameCase = ClinicalCase.rehydrate(clinicalCase.id(), PSYCHOLOGIST_ID, PATIENT_ID,
                "Different concern", ClinicalCaseStatus.ACTIVE, NOW, null, NOW, NOW.plusSeconds(1));
        assertThat(clinicalCase).isEqualTo(sameCase).hasSameHashCodeAs(sameCase);
        assertThat(clinicalCase).isNotEqualTo(new Object());
        assertThat(clinicalCase.psychologistId()).isEqualTo(PSYCHOLOGIST_ID);
        assertThat(clinicalCase.patientId()).isEqualTo(PATIENT_ID);
        assertThat(clinicalCase.presentingConcern()).isNotBlank();
        assertThat(clinicalCase.openedAt()).isEqualTo(NOW);
        assertThat(clinicalCase.createdAt()).isEqualTo(NOW);
        assertThat(clinicalCase.updatedAt()).isEqualTo(NOW);

        PracticeProfile profile = PracticeProfile.create(PSYCHOLOGIST_ID, "OPP 123",
                ZoneId.of("Europe/Lisbon"), 50, "Lisbon", true, NOW);
        PracticeProfile sameProfile = PracticeProfile.rehydrate(PSYCHOLOGIST_ID, null,
                ZoneId.of("UTC"), 60, null, false, NOW, NOW.plusSeconds(1));
        assertThat(profile).isEqualTo(sameProfile).hasSameHashCodeAs(sameProfile);
        assertThat(profile).isNotEqualTo(new Object());
        assertThat(profile.psychologistId()).isEqualTo(PSYCHOLOGIST_ID);
        assertThat(profile.timezone()).isEqualTo(ZoneId.of("Europe/Lisbon"));
        assertThat(profile.defaultSessionDurationMinutes()).isEqualTo(50);
        assertThat(profile.primaryLocation()).isEqualTo("Lisbon");
        assertThat(profile.telehealthEnabled()).isTrue();
        assertThat(profile.createdAt()).isEqualTo(NOW);
        assertThat(profile.updatedAt()).isEqualTo(NOW);

        CareGoalId goalId = new CareGoalId(UUID.randomUUID());
        CareGoal goal = new CareGoal(goalId, "Goal", LocalDate.of(2026, 7, 1),
                GoalStatus.IN_PROGRESS, 40);
        CareGoal sameGoal = new CareGoal(goalId, "Other", null, GoalStatus.ACHIEVED, 100);
        assertThat(goal).isEqualTo(sameGoal).hasSameHashCodeAs(sameGoal);
        assertThat(goal).isNotEqualTo(new Object());
        assertThat(goal.id()).isEqualTo(goalId);
        assertThat(goal.description()).isEqualTo("Goal");
        assertThat(goal.targetDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(goal.status()).isEqualTo(GoalStatus.IN_PROGRESS);
        assertThat(goal.progressPercentage()).isEqualTo(40);

        CarePlanId planId = new CarePlanId(UUID.randomUUID());
        CarePlan plan = CarePlan.create(planId, clinicalCase, "Focus", "Weekly",
                LocalDate.of(2026, 7, 15), List.of(goal), NOW);
        CarePlan samePlan = CarePlan.rehydrate(planId, clinicalCase.id(), PSYCHOLOGIST_ID, PATIENT_ID,
                "Other", "Monthly", LocalDate.of(2026, 8, 1), List.of(sameGoal), NOW, NOW);
        assertThat(plan).isEqualTo(samePlan).hasSameHashCodeAs(samePlan);
        assertThat(plan).isNotEqualTo(new Object());
        assertThat(plan.id()).isEqualTo(planId);
        assertThat(plan.clinicalCaseId()).isEqualTo(clinicalCase.id());
        assertThat(plan.psychologistId()).isEqualTo(PSYCHOLOGIST_ID);
        assertThat(plan.patientId()).isEqualTo(PATIENT_ID);
        assertThat(plan.therapeuticFocus()).isEqualTo("Focus");
        assertThat(plan.plannedFrequency()).isEqualTo("Weekly");
        assertThat(plan.reviewDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(plan.goals()).containsExactly(goal);
        assertThat(plan.createdAt()).isEqualTo(NOW);
        assertThat(plan.updatedAt()).isEqualTo(NOW);

        SessionRecordId sessionId = new SessionRecordId(UUID.randomUUID());
        AppointmentId appointmentId = new AppointmentId(UUID.randomUUID());
        SessionRecord session = SessionRecord.rehydrate(sessionId, clinicalCase.id(), PSYCHOLOGIST_ID,
                PATIENT_ID, appointmentId, NOW, SessionModality.ONLINE, 50,
                AttendanceStatus.ATTENDED, NoteStatus.SIGNED, "Summary", "Focus", "CBT",
                "Homework", "Next", NOW, NOW.plusSeconds(1));
        SessionRecord sameSession = SessionRecord.fromCompletedAppointment(sessionId, null, PSYCHOLOGIST_ID,
                PATIENT_ID, appointmentId, NOW, SessionModality.IN_PERSON, 45, NOW);
        assertThat(session).isEqualTo(sameSession).hasSameHashCodeAs(sameSession);
        assertThat(session).isNotEqualTo(new Object());
        assertThat(session.id()).isEqualTo(sessionId);
        assertThat(session.clinicalCaseId()).contains(clinicalCase.id());
        assertThat(session.psychologistId()).isEqualTo(PSYCHOLOGIST_ID);
        assertThat(session.patientId()).isEqualTo(PATIENT_ID);
        assertThat(session.appointmentId()).contains(appointmentId);
        assertThat(session.sessionDate()).isEqualTo(NOW);
        assertThat(session.modality()).isEqualTo(SessionModality.ONLINE);
        assertThat(session.durationMinutes()).isEqualTo(50);
        assertThat(session.attendanceStatus()).isEqualTo(AttendanceStatus.ATTENDED);
        assertThat(session.noteStatus()).isEqualTo(NoteStatus.SIGNED);
        assertThat(session.summary()).isEqualTo("Summary");
        assertThat(session.focusAreas()).isEqualTo("Focus");
        assertThat(session.interventions()).isEqualTo("CBT");
        assertThat(session.homework()).isEqualTo("Homework");
        assertThat(session.nextSteps()).isEqualTo("Next");
        assertThat(session.createdAt()).isEqualTo(NOW);
        assertThat(session.updatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void enforcesAggregateBoundaryValues() {
        assertThatThrownBy(() -> PracticeProfile.create(PSYCHOLOGIST_ID, null, ZoneId.of("UTC"),
                241, null, false, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CareGoal(new CareGoalId(UUID.randomUUID()), "Goal", null,
                GoalStatus.NOT_STARTED, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SessionRecord.fromCompletedAppointment(new SessionRecordId(UUID.randomUUID()),
                null, PSYCHOLOGIST_ID, PATIENT_ID, null, NOW, SessionModality.ONLINE, -1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SessionRecord.fromCompletedAppointment(new SessionRecordId(UUID.randomUUID()),
                null, PSYCHOLOGIST_ID, PATIENT_ID, null, NOW, SessionModality.ONLINE, 481, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingPracticeProfileFieldsAndInvalidUpdates() {
        assertThatThrownBy(() -> PracticeProfile.rehydrate(null, null, ZoneId.of("UTC"),
                50, null, false, NOW, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PracticeProfile.rehydrate(PSYCHOLOGIST_ID, null, null,
                50, null, false, NOW, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PracticeProfile.rehydrate(PSYCHOLOGIST_ID, null, ZoneId.of("UTC"),
                50, null, false, null, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PracticeProfile.rehydrate(PSYCHOLOGIST_ID, null, ZoneId.of("UTC"),
                50, null, false, NOW, null)).isInstanceOf(IllegalArgumentException.class);

        PracticeProfile profile = PracticeProfile.create(PSYCHOLOGIST_ID, null, ZoneId.of("UTC"),
                50, null, false, NOW);
        assertThatThrownBy(() -> profile.update(null, null, 50, null, false, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.update(null, ZoneId.of("UTC"), 50, null, false, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.update(null, ZoneId.of("UTC"), 50, null, false,
                NOW.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> profile.update(null, ZoneId.of("UTC"), 14, null, false, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingClinicalCaseFieldsAndInvalidStatusChanges() {
        assertInvalidCase(null, PSYCHOLOGIST_ID, PATIENT_ID, ClinicalCaseStatus.ACTIVE, NOW, NOW, NOW);
        assertInvalidCase(new ClinicalCaseId(UUID.randomUUID()), null, PATIENT_ID,
                ClinicalCaseStatus.ACTIVE, NOW, NOW, NOW);
        assertInvalidCase(new ClinicalCaseId(UUID.randomUUID()), PSYCHOLOGIST_ID, null,
                ClinicalCaseStatus.ACTIVE, NOW, NOW, NOW);
        assertInvalidCase(new ClinicalCaseId(UUID.randomUUID()), PSYCHOLOGIST_ID, PATIENT_ID,
                null, NOW, NOW, NOW);
        assertInvalidCase(new ClinicalCaseId(UUID.randomUUID()), PSYCHOLOGIST_ID, PATIENT_ID,
                ClinicalCaseStatus.ACTIVE, null, NOW, NOW);
        assertInvalidCase(new ClinicalCaseId(UUID.randomUUID()), PSYCHOLOGIST_ID, PATIENT_ID,
                ClinicalCaseStatus.ACTIVE, NOW, null, NOW);
        assertInvalidCase(new ClinicalCaseId(UUID.randomUUID()), PSYCHOLOGIST_ID, PATIENT_ID,
                ClinicalCaseStatus.ACTIVE, NOW, NOW, null);

        ClinicalCase clinicalCase = clinicalCase();
        assertThatThrownBy(() -> clinicalCase.changeStatus(null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> clinicalCase.changeStatus(ClinicalCaseStatus.ACTIVE, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> clinicalCase.changeStatus(ClinicalCaseStatus.ACTIVE, NOW.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingCarePlanFieldsAndInvalidReplacements() {
        CareGoal goal = goal();
        assertInvalidPlan(null, new ClinicalCaseId(UUID.randomUUID()), PSYCHOLOGIST_ID, PATIENT_ID,
                LocalDate.now(), List.of(goal), NOW, NOW);
        assertInvalidPlan(new CarePlanId(UUID.randomUUID()), null, PSYCHOLOGIST_ID, PATIENT_ID,
                LocalDate.now(), List.of(goal), NOW, NOW);
        assertInvalidPlan(new CarePlanId(UUID.randomUUID()), new ClinicalCaseId(UUID.randomUUID()), null, PATIENT_ID,
                LocalDate.now(), List.of(goal), NOW, NOW);
        assertInvalidPlan(new CarePlanId(UUID.randomUUID()), new ClinicalCaseId(UUID.randomUUID()),
                PSYCHOLOGIST_ID, null, LocalDate.now(), List.of(goal), NOW, NOW);
        assertInvalidPlan(new CarePlanId(UUID.randomUUID()), new ClinicalCaseId(UUID.randomUUID()),
                PSYCHOLOGIST_ID, PATIENT_ID, null, List.of(goal), NOW, NOW);
        assertInvalidPlan(new CarePlanId(UUID.randomUUID()), new ClinicalCaseId(UUID.randomUUID()),
                PSYCHOLOGIST_ID, PATIENT_ID, LocalDate.now(), null, NOW, NOW);
        assertInvalidPlan(new CarePlanId(UUID.randomUUID()), new ClinicalCaseId(UUID.randomUUID()),
                PSYCHOLOGIST_ID, PATIENT_ID, LocalDate.now(), List.of(goal), null, NOW);
        assertInvalidPlan(new CarePlanId(UUID.randomUUID()), new ClinicalCaseId(UUID.randomUUID()),
                PSYCHOLOGIST_ID, PATIENT_ID, LocalDate.now(), List.of(goal), NOW, null);

        CarePlan plan = CarePlan.create(new CarePlanId(UUID.randomUUID()), clinicalCase(), "Focus", "Weekly",
                LocalDate.now(), List.of(goal), NOW);
        assertThatThrownBy(() -> plan.replace("Focus", "Weekly", LocalDate.now(), List.of(goal), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> plan.replace("Focus", "Weekly", LocalDate.now(), List.of(goal),
                NOW.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> plan.replace("Focus", "Weekly", LocalDate.now(), null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> plan.replace("Focus", "Weekly", LocalDate.now(), List.of(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> plan.replace("Focus", "Weekly", null, List.of(goal), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingSessionFieldsAndInvalidUpdates() {
        SessionRecordId id = new SessionRecordId(UUID.randomUUID());
        assertInvalidSession(null, PSYCHOLOGIST_ID, PATIENT_ID, NOW, SessionModality.ONLINE,
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, NOW, NOW);
        assertInvalidSession(id, null, PATIENT_ID, NOW, SessionModality.ONLINE,
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, NOW, NOW);
        assertInvalidSession(id, PSYCHOLOGIST_ID, null, NOW, SessionModality.ONLINE,
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, NOW, NOW);
        assertInvalidSession(id, PSYCHOLOGIST_ID, PATIENT_ID, null, SessionModality.ONLINE,
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, NOW, NOW);
        assertInvalidSession(id, PSYCHOLOGIST_ID, PATIENT_ID, NOW, null,
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, NOW, NOW);
        assertInvalidSession(id, PSYCHOLOGIST_ID, PATIENT_ID, NOW, SessionModality.ONLINE,
                null, NoteStatus.DRAFT, NOW, NOW);
        assertInvalidSession(id, PSYCHOLOGIST_ID, PATIENT_ID, NOW, SessionModality.ONLINE,
                AttendanceStatus.ATTENDED, null, NOW, NOW);
        assertInvalidSession(id, PSYCHOLOGIST_ID, PATIENT_ID, NOW, SessionModality.ONLINE,
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, null, NOW);
        assertInvalidSession(id, PSYCHOLOGIST_ID, PATIENT_ID, NOW, SessionModality.ONLINE,
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, NOW, null);

        SessionRecord session = SessionRecord.fromCompletedAppointment(id, null, PSYCHOLOGIST_ID, PATIENT_ID,
                null, NOW, SessionModality.ONLINE, 50, NOW);
        assertInvalidUpdate(session, null, SessionModality.ONLINE, AttendanceStatus.ATTENDED, NoteStatus.DRAFT, NOW);
        assertInvalidUpdate(session, NOW, null, AttendanceStatus.ATTENDED, NoteStatus.DRAFT, NOW);
        assertInvalidUpdate(session, NOW, SessionModality.ONLINE, null, NoteStatus.DRAFT, NOW);
        assertInvalidUpdate(session, NOW, SessionModality.ONLINE, AttendanceStatus.ATTENDED, null, NOW);
        assertInvalidUpdate(session, NOW, SessionModality.ONLINE, AttendanceStatus.ATTENDED, NoteStatus.DRAFT, null);
        assertInvalidUpdate(session, NOW, SessionModality.ONLINE, AttendanceStatus.ATTENDED, NoteStatus.DRAFT,
                NOW.minusSeconds(1));

        assertThat(SessionRecord.manual(new SessionRecordId(UUID.randomUUID()), clinicalCase(), null, NOW,
                SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED, null, null, null, null, null, NOW)
                .noteStatus()).isEqualTo(NoteStatus.PENDING_NOTE);
        assertThat(SessionRecord.manual(new SessionRecordId(UUID.randomUUID()), clinicalCase(), null, NOW,
                SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED, "  ", null, null, null, null, NOW)
                .noteStatus()).isEqualTo(NoteStatus.PENDING_NOTE);
    }

    @Test
    void validatesTextNormalizationIdentifiersAndIdentityBranches() {
        assertThat(TextFields.required(" value ", 5, "invalid")).isEqualTo("value");
        assertThat(TextFields.optional(null, 5, "invalid")).isNull();
        assertThat(TextFields.optional("  ", 5, "invalid")).isNull();
        assertThatThrownBy(() -> TextFields.required(null, 5, "invalid")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TextFields.required(" ", 5, "invalid")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TextFields.optional("123456", 5, "invalid"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new AppointmentId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CareGoalId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CarePlanId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClinicalCaseId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DomainEventId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PatientId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PsychologistId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SessionRecordId(null)).isInstanceOf(IllegalArgumentException.class);

        ClinicalCase clinicalCase = clinicalCase();
        CareGoal goal = goal();
        CarePlan plan = CarePlan.create(new CarePlanId(UUID.randomUUID()), clinicalCase, "Focus", "Weekly",
                LocalDate.now(), List.of(goal), NOW);
        PracticeProfile profile = PracticeProfile.create(PSYCHOLOGIST_ID, null, ZoneId.of("UTC"), 50, null, true, NOW);
        SessionRecord session = SessionRecord.fromCompletedAppointment(new SessionRecordId(UUID.randomUUID()),
                clinicalCase.id(), PSYCHOLOGIST_ID, PATIENT_ID, null, NOW, SessionModality.ONLINE, 50, NOW);

        assertThat(clinicalCase).isEqualTo(clinicalCase).isNotEqualTo(clinicalCase());
        assertThat(goal).isEqualTo(goal).isNotEqualTo(goal());
        assertThat(plan).isEqualTo(plan).isNotEqualTo(CarePlan.create(new CarePlanId(UUID.randomUUID()),
                clinicalCase, "Focus", "Weekly", LocalDate.now(), List.of(goal), NOW));
        assertThat(profile).isEqualTo(profile).isNotEqualTo(PracticeProfile.create(
                new PsychologistId(UUID.randomUUID()), null, ZoneId.of("UTC"), 50, null, true, NOW));
        assertThat(session).isEqualTo(session).isNotEqualTo(SessionRecord.fromCompletedAppointment(
                new SessionRecordId(UUID.randomUUID()), clinicalCase.id(), PSYCHOLOGIST_ID, PATIENT_ID,
                null, NOW, SessionModality.ONLINE, 50, NOW));
    }

    private void assertInvalidCase(ClinicalCaseId id, PsychologistId psychologistId, PatientId patientId,
                                   ClinicalCaseStatus status, Instant openedAt, Instant createdAt, Instant updatedAt) {
        assertThatThrownBy(() -> ClinicalCase.rehydrate(id, psychologistId, patientId, "Concern", status,
                openedAt, null, createdAt, updatedAt)).isInstanceOf(IllegalArgumentException.class);
    }

    private void assertInvalidPlan(CarePlanId id, ClinicalCaseId caseId, PsychologistId psychologistId,
                                   PatientId patientId, LocalDate reviewDate, List<CareGoal> goals,
                                   Instant createdAt, Instant updatedAt) {
        assertThatThrownBy(() -> CarePlan.rehydrate(id, caseId, psychologistId, patientId, "Focus", "Weekly",
                reviewDate, goals, createdAt, updatedAt)).isInstanceOf(IllegalArgumentException.class);
    }

    private void assertInvalidSession(SessionRecordId id, PsychologistId psychologistId, PatientId patientId,
                                      Instant sessionDate, SessionModality modality, AttendanceStatus attendance,
                                      NoteStatus noteStatus, Instant createdAt, Instant updatedAt) {
        assertThatThrownBy(() -> SessionRecord.rehydrate(id, null, psychologistId, patientId, null, sessionDate,
                modality, 50, attendance, noteStatus, null, null, null, null, null, createdAt, updatedAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertInvalidUpdate(SessionRecord session, Instant sessionDate, SessionModality modality,
                                     AttendanceStatus attendance, NoteStatus noteStatus, Instant updatedAt) {
        assertThatThrownBy(() -> session.update(null, sessionDate, modality, 50, attendance, noteStatus,
                null, null, null, null, null, updatedAt)).isInstanceOf(IllegalArgumentException.class);
    }

    private CareGoal goal() {
        return new CareGoal(new CareGoalId(UUID.randomUUID()), "Goal", null, GoalStatus.NOT_STARTED, 0);
    }

    private ClinicalCase clinicalCase() {
        return ClinicalCase.create(new ClinicalCaseId(UUID.randomUUID()), PSYCHOLOGIST_ID, PATIENT_ID,
                "Anxiety symptoms affecting work", NOW);
    }
}
