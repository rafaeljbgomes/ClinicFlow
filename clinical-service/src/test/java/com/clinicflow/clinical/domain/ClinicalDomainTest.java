package com.clinicflow.clinical.domain;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ClinicalDomainTest {
    private static final PsychologistId PSYCHOLOGIST_ID =
            new PsychologistId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private static final PatientId PATIENT_ID =
            new PatientId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    private static final PsychologistId OTHER_PSYCHOLOGIST_ID =
            new PsychologistId(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"));
    private static final PatientId OTHER_PATIENT_ID =
            new PatientId(UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"));
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final ZoneId UTC = ZoneId.of("UTC");

    static Stream<Arguments> invalidPracticeProfiles() {
        return Stream.of(
                Arguments.of("duration above maximum", (ThrowingCallable) () ->
                        PracticeProfile.create(PSYCHOLOGIST_ID, null, UTC, 241, null, false, NOW)),
                Arguments.of("missing psychologist", (ThrowingCallable) () ->
                        PracticeProfile.rehydrate(null, null, UTC, 50, null, false, NOW, NOW)),
                Arguments.of("missing timezone", (ThrowingCallable) () ->
                        PracticeProfile.rehydrate(PSYCHOLOGIST_ID, null, null, 50, null, false, NOW, NOW)),
                Arguments.of("missing createdAt", (ThrowingCallable) () ->
                        PracticeProfile.rehydrate(PSYCHOLOGIST_ID, null, UTC, 50, null, false, null, NOW)),
                Arguments.of("missing updatedAt", (ThrowingCallable) () ->
                        PracticeProfile.rehydrate(PSYCHOLOGIST_ID, null, UTC, 50, null, false, NOW, null)));
    }

    static Stream<Arguments> invalidPracticeProfileUpdates() {
        return Stream.of(
                Arguments.of("duration 10", UTC, 10, NOW),
                Arguments.of("duration 14", UTC, 14, NOW),
                Arguments.of("missing timezone", null, 50, NOW),
                Arguments.of("missing updatedAt", UTC, 50, null),
                Arguments.of("updatedAt before createdAt", UTC, 50, NOW.minusSeconds(1)));
    }

    static Stream<Arguments> invalidClinicalCases() {
        ClinicalCaseId id = new ClinicalCaseId(UUID.randomUUID());
        return Stream.of(
                Arguments.of("missing id", null, PSYCHOLOGIST_ID, PATIENT_ID, ClinicalCaseStatus.ACTIVE, NOW, NOW, NOW),
                Arguments.of("missing psychologist", id, null, PATIENT_ID, ClinicalCaseStatus.ACTIVE, NOW, NOW, NOW),
                Arguments.of("missing patient", id, PSYCHOLOGIST_ID, null, ClinicalCaseStatus.ACTIVE, NOW, NOW, NOW),
                Arguments.of("missing status", id, PSYCHOLOGIST_ID, PATIENT_ID, null, NOW, NOW, NOW),
                Arguments.of("missing openedAt", id, PSYCHOLOGIST_ID, PATIENT_ID, ClinicalCaseStatus.ACTIVE, null, NOW,
                        NOW),
                Arguments.of("missing createdAt", id, PSYCHOLOGIST_ID, PATIENT_ID, ClinicalCaseStatus.ACTIVE, NOW, null,
                        NOW),
                Arguments.of("missing updatedAt", id, PSYCHOLOGIST_ID, PATIENT_ID, ClinicalCaseStatus.ACTIVE, NOW, NOW,
                        null));
    }

    static Stream<Arguments> invalidStatusChanges() {
        return Stream.of(
                Arguments.of("missing status", null, NOW),
                Arguments.of("missing changedAt", ClinicalCaseStatus.ACTIVE, null),
                Arguments.of("changedAt before updatedAt", ClinicalCaseStatus.ACTIVE, NOW.minusSeconds(1)));
    }

    static Stream<Arguments> invalidCarePlans() {
        CarePlanId id = new CarePlanId(UUID.randomUUID());
        ClinicalCaseId caseId = new ClinicalCaseId(UUID.randomUUID());
        LocalDate review = LocalDate.of(2026, 7, 15);
        List<CareGoal> goals = List.of(goal());
        return Stream.of(
                Arguments.of("missing id", null, caseId, PSYCHOLOGIST_ID, PATIENT_ID, review, goals, NOW, NOW),
                Arguments.of("missing case", id, null, PSYCHOLOGIST_ID, PATIENT_ID, review, goals, NOW, NOW),
                Arguments.of("missing psychologist", id, caseId, null, PATIENT_ID, review, goals, NOW, NOW),
                Arguments.of("missing patient", id, caseId, PSYCHOLOGIST_ID, null, review, goals, NOW, NOW),
                Arguments.of("missing review date", id, caseId, PSYCHOLOGIST_ID, PATIENT_ID, null, goals, NOW, NOW),
                Arguments.of("missing goals", id, caseId, PSYCHOLOGIST_ID, PATIENT_ID, review, null, NOW, NOW),
                Arguments.of("missing createdAt", id, caseId, PSYCHOLOGIST_ID, PATIENT_ID, review, goals, null, NOW),
                Arguments.of("missing updatedAt", id, caseId, PSYCHOLOGIST_ID, PATIENT_ID, review, goals, NOW, null));
    }

    static Stream<Arguments> invalidCarePlanReplacements() {
        CareGoal goal = goal();
        LocalDate review = LocalDate.of(2026, 7, 15);
        return Stream.of(
                Arguments.of("missing updatedAt", review, List.of(goal), null),
                Arguments.of("updatedAt before createdAt", review, List.of(goal), NOW.minusSeconds(1)),
                Arguments.of("missing goals", review, null, NOW),
                Arguments.of("no goals", review, List.of(), NOW),
                Arguments.of("missing review date", null, List.of(goal), NOW),
                Arguments.of("duplicate goal ids", review,
                        List.of(goal, new CareGoal(goal.id(), "Same id", null, GoalStatus.NOT_STARTED, 0)), NOW));
    }

    static Stream<Arguments> invalidSessionRecords() {
        SessionRecordId id = new SessionRecordId(UUID.randomUUID());
        return Stream.of(
                Arguments.of("missing id", null, PSYCHOLOGIST_ID, PATIENT_ID, NOW, SessionModality.ONLINE,
                        AttendanceStatus.ATTENDED, NoteStatus.DRAFT, NOW, NOW),
                Arguments.of("missing psychologist", id, null, PATIENT_ID, NOW, SessionModality.ONLINE,
                        AttendanceStatus.ATTENDED, NoteStatus.DRAFT, NOW, NOW),
                Arguments.of("missing patient", id, PSYCHOLOGIST_ID, null, NOW, SessionModality.ONLINE,
                        AttendanceStatus.ATTENDED, NoteStatus.DRAFT, NOW, NOW),
                Arguments.of("missing session date", id, PSYCHOLOGIST_ID, PATIENT_ID, null, SessionModality.ONLINE,
                        AttendanceStatus.ATTENDED, NoteStatus.DRAFT, NOW, NOW),
                Arguments.of("missing modality", id, PSYCHOLOGIST_ID, PATIENT_ID, NOW, null,
                        AttendanceStatus.ATTENDED, NoteStatus.DRAFT, NOW, NOW),
                Arguments.of("missing attendance", id, PSYCHOLOGIST_ID, PATIENT_ID, NOW, SessionModality.ONLINE,
                        null, NoteStatus.DRAFT, NOW, NOW),
                Arguments.of("missing note status", id, PSYCHOLOGIST_ID, PATIENT_ID, NOW, SessionModality.ONLINE,
                        AttendanceStatus.ATTENDED, null, NOW, NOW),
                Arguments.of("missing createdAt", id, PSYCHOLOGIST_ID, PATIENT_ID, NOW, SessionModality.ONLINE,
                        AttendanceStatus.ATTENDED, NoteStatus.DRAFT, null, NOW),
                Arguments.of("missing updatedAt", id, PSYCHOLOGIST_ID, PATIENT_ID, NOW, SessionModality.ONLINE,
                        AttendanceStatus.ATTENDED, NoteStatus.DRAFT, NOW, null));
    }

    static Stream<Arguments> invalidSessionUpdates() {
        return Stream.of(
                Arguments.of("missing session date", null, SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED,
                        NoteStatus.DRAFT, NOW),
                Arguments.of("missing modality", NOW, null, 50, AttendanceStatus.ATTENDED, NoteStatus.DRAFT, NOW),
                Arguments.of("missing attendance", NOW, SessionModality.ONLINE, 50, null, NoteStatus.DRAFT, NOW),
                Arguments.of("missing note status", NOW, SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED, null,
                        NOW),
                Arguments.of("missing updatedAt", NOW, SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED,
                        NoteStatus.DRAFT, null),
                Arguments.of("updatedAt before createdAt", NOW, SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED,
                        NoteStatus.DRAFT, NOW.minusSeconds(1)),
                Arguments.of("duration above maximum", NOW, SessionModality.ONLINE, 500, AttendanceStatus.ATTENDED,
                        NoteStatus.DRAFT, NOW));
    }

    static Stream<Arguments> notSignableNotes() {
        return Stream.of(
                Arguments.of("pending note without summary", SessionRecord.fromCompletedAppointment(
                        new SessionRecordId(UUID.randomUUID()), null, PSYCHOLOGIST_ID, PATIENT_ID, null, NOW,
                        SessionModality.ONLINE, 50, NOW)),
                Arguments.of("pending note with summary", session(NoteStatus.PENDING_NOTE, "Summary")),
                Arguments.of("draft with blank summary", session(NoteStatus.DRAFT, "   ")));
    }

    static Stream<Arguments> invalidSignatures() {
        return Stream.of(
                Arguments.of("another psychologist", new PsychologistId(UUID.randomUUID()), NOW),
                Arguments.of("missing signer", null, NOW),
                Arguments.of("missing signedAt", PSYCHOLOGIST_ID, null),
                Arguments.of("signedAt before updatedAt", PSYCHOLOGIST_ID, NOW.minusSeconds(1)));
    }

    static Stream<Arguments> inconsistentSignatures() {
        return Stream.of(
                Arguments.of(NoteStatus.SIGNED, null, null),
                Arguments.of(NoteStatus.SIGNED, NOW, null),
                Arguments.of(NoteStatus.SIGNED, null, PSYCHOLOGIST_ID),
                Arguments.of(NoteStatus.DRAFT, NOW, PSYCHOLOGIST_ID),
                Arguments.of(NoteStatus.DRAFT, NOW, null),
                Arguments.of(NoteStatus.PENDING_NOTE, null, PSYCHOLOGIST_ID));
    }

    static Stream<Arguments> nullIdentifiers() {
        return Stream.of(
                Arguments.of("AppointmentId", (ThrowingCallable) () -> new AppointmentId(null)),
                Arguments.of("CareGoalId", (ThrowingCallable) () -> new CareGoalId(null)),
                Arguments.of("CarePlanId", (ThrowingCallable) () -> new CarePlanId(null)),
                Arguments.of("ClinicalCaseId", (ThrowingCallable) () -> new ClinicalCaseId(null)),
                Arguments.of("DomainEventId", (ThrowingCallable) () -> new DomainEventId(null)),
                Arguments.of("PatientId", (ThrowingCallable) () -> new PatientId(null)),
                Arguments.of("PsychologistId", (ThrowingCallable) () -> new PsychologistId(null)),
                Arguments.of("SessionRecordId", (ThrowingCallable) () -> new SessionRecordId(null)),
                Arguments.of("SessionRecordLinkConflictId",
                        (ThrowingCallable) () -> new SessionRecordLinkConflictId(null)));
    }

    static Stream<Arguments> appointmentOwnersOtherThanTheRecord() {
        return Stream.of(
                Arguments.of("another psychologist", OTHER_PSYCHOLOGIST_ID, PATIENT_ID),
                Arguments.of("another patient", PSYCHOLOGIST_ID, OTHER_PATIENT_ID),
                Arguments.of("another psychologist and patient", OTHER_PSYCHOLOGIST_ID, OTHER_PATIENT_ID));
    }

    static Stream<Arguments> invalidLinkConflicts() {
        SessionRecordLinkConflictId id = new SessionRecordLinkConflictId(UUID.randomUUID());
        SessionRecordId sessionId = new SessionRecordId(UUID.randomUUID());
        AppointmentId appointmentId = new AppointmentId(UUID.randomUUID());
        LinkConflictSource source = LinkConflictSource.MANUAL_LINK;
        return Stream.of(
                Arguments.of("missing id", null, sessionId, appointmentId, OTHER_PSYCHOLOGIST_ID, PATIENT_ID, source,
                        NOW),
                Arguments.of("missing session record", id, null, appointmentId, OTHER_PSYCHOLOGIST_ID, PATIENT_ID,
                        source, NOW),
                Arguments.of("missing appointment", id, sessionId, null, OTHER_PSYCHOLOGIST_ID, PATIENT_ID, source,
                        NOW),
                Arguments.of("missing appointment psychologist", id, sessionId, appointmentId, null, PATIENT_ID,
                        source, NOW),
                Arguments.of("missing appointment patient", id, sessionId, appointmentId, OTHER_PSYCHOLOGIST_ID,
                        null, source, NOW),
                Arguments.of("missing source", id, sessionId, appointmentId, OTHER_PSYCHOLOGIST_ID, PATIENT_ID, null,
                        NOW),
                Arguments.of("missing detectedAt", id, sessionId, appointmentId, OTHER_PSYCHOLOGIST_ID, PATIENT_ID,
                        source, null));
    }

    static Stream<Arguments> sameIdentityPairs() {
        ClinicalCase clinicalCase = clinicalCase();
        CareGoal goal = goal();
        CarePlanId planId = new CarePlanId(UUID.randomUUID());
        SessionRecordId sessionId = new SessionRecordId(UUID.randomUUID());
        AppointmentId appointmentId = new AppointmentId(UUID.randomUUID());
        return Stream.of(
                Arguments.of("clinical case", clinicalCase, ClinicalCase.rehydrate(clinicalCase.id(), PSYCHOLOGIST_ID,
                        PATIENT_ID, "Different concern", ClinicalCaseStatus.ACTIVE, NOW, null, NOW,
                        NOW.plusSeconds(1))),
                Arguments.of("practice profile",
                        PracticeProfile.create(PSYCHOLOGIST_ID, "OPP 123", ZoneId.of("Europe/Lisbon"), 50, "Lisbon",
                                true, NOW),
                        PracticeProfile.rehydrate(PSYCHOLOGIST_ID, null, UTC, 60, null, false, NOW,
                                NOW.plusSeconds(1))),
                Arguments.of("care goal", goal, new CareGoal(goal.id(), "Other", null, GoalStatus.ACHIEVED, 100)),
                Arguments.of("care plan",
                        CarePlan.create(planId, clinicalCase, "Focus", "Weekly", LocalDate.of(2026, 7, 15),
                                List.of(goal), NOW),
                        CarePlan.rehydrate(planId, clinicalCase.id(), PSYCHOLOGIST_ID, PATIENT_ID, "Other", "Monthly",
                                LocalDate.of(2026, 8, 1), List.of(goal), NOW, NOW)),
                Arguments.of("session record",
                        SessionRecord.rehydrate(sessionId, clinicalCase.id(), PSYCHOLOGIST_ID, PATIENT_ID,
                                appointmentId, NOW, SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED,
                                NoteStatus.SIGNED, "Summary", null, null, null, null, NOW, NOW, NOW, PSYCHOLOGIST_ID),
                        SessionRecord.fromCompletedAppointment(sessionId, null, PSYCHOLOGIST_ID, PATIENT_ID,
                                appointmentId, NOW, SessionModality.IN_PERSON, 45, NOW)));
    }

    static Stream<Arguments> differentIdentityPairs() {
        ClinicalCase clinicalCase = clinicalCase();
        return Stream.of(
                Arguments.of("clinical case", clinicalCase, clinicalCase()),
                Arguments.of("care goal", goal(), goal()),
                Arguments.of("care plan", plan(clinicalCase), plan(clinicalCase)),
                Arguments.of("practice profile",
                        PracticeProfile.create(PSYCHOLOGIST_ID, null, UTC, 50, null, true, NOW),
                        PracticeProfile.create(new PsychologistId(UUID.randomUUID()), null, UTC, 50, null, true, NOW)),
                Arguments.of("session record", pendingSession(clinicalCase.id()), pendingSession(clinicalCase.id())));
    }

    static Stream<Arguments> aggregates() {
        return sameIdentityPairs().map(arguments -> Arguments.of(arguments.get()[0], arguments.get()[1]));
    }

    @Test
    void practiceProfileUpdateMutatesTheSameInstance() {
        // Arrange
        PracticeProfile profile = practiceProfile();

        // Act
        PracticeProfile changed = profile.update("OPP 456", UTC, 60, null, false, NOW.plusSeconds(60));

        // Assert
        assertThat(changed).isSameAs(profile);
    }

    @Test
    void practiceProfileUpdateReplacesTheProfileFields() {
        // Arrange
        PracticeProfile profile = practiceProfile();

        // Act
        profile.update("OPP 456", UTC, 60, null, false, NOW.plusSeconds(60));

        // Assert
        assertThat(profile.professionalRegistration()).isEqualTo("OPP 456");
        assertThat(profile.defaultSessionDurationMinutes()).isEqualTo(60);
        assertThat(profile.primaryLocation()).isNull();
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidPracticeProfiles")
    void rejectsInvalidPracticeProfile(String invalid, ThrowingCallable build) {
        // Act
        Throwable thrown = catchThrowable(build);

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidPracticeProfileUpdates")
    void rejectsInvalidPracticeProfileUpdate(String invalid, ZoneId timezone, int duration, Instant updatedAt) {
        // Arrange
        PracticeProfile profile = practiceProfile();

        // Act
        Throwable thrown = catchThrowable(() -> profile.update("x", timezone, duration, null, false, updatedAt));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void activatingACaseKeepsItOpen() {
        // Arrange
        ClinicalCase clinicalCase = clinicalCase();

        // Act
        clinicalCase.changeStatus(ClinicalCaseStatus.ACTIVE, NOW.plusSeconds(60));

        // Assert
        assertThat(clinicalCase.status()).isEqualTo(ClinicalCaseStatus.ACTIVE);
        assertThat(clinicalCase.closedAt()).isNull();
    }

    @Test
    void dischargingACaseRecordsItsClosure() {
        // Arrange
        ClinicalCase clinicalCase = clinicalCase();

        // Act
        clinicalCase.changeStatus(ClinicalCaseStatus.DISCHARGED, NOW.plusSeconds(120));

        // Assert
        assertThat(clinicalCase.status()).isEqualTo(ClinicalCaseStatus.DISCHARGED);
        assertThat(clinicalCase.closedAt()).isEqualTo(NOW.plusSeconds(120));
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidClinicalCases")
    void rejectsClinicalCaseWithMissingField(String invalid, ClinicalCaseId id, PsychologistId psychologistId,
                                             PatientId patientId, ClinicalCaseStatus status, Instant openedAt,
                                             Instant createdAt, Instant updatedAt) {
        // Act
        Throwable thrown = catchThrowable(() -> ClinicalCase.rehydrate(id, psychologistId, patientId, "Concern",
                status, openedAt, null, createdAt, updatedAt));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidStatusChanges")
    void rejectsInvalidCaseStatusChange(String invalid, ClinicalCaseStatus status, Instant changedAt) {
        // Arrange
        ClinicalCase clinicalCase = clinicalCase();

        // Act
        Throwable thrown = catchThrowable(() -> clinicalCase.changeStatus(status, changedAt));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void carePlanKeepsItsGoals() {
        // Arrange
        CareGoal goal = new CareGoal(new CareGoalId(UUID.randomUUID()), "Sleep routine",
                LocalDate.of(2026, 7, 1), GoalStatus.IN_PROGRESS, 40);

        // Act
        CarePlan plan = CarePlan.create(new CarePlanId(UUID.randomUUID()), clinicalCase(),
                "Reduce avoidance", "Weekly", LocalDate.of(2026, 7, 15), List.of(goal), NOW);

        // Assert
        assertThat(plan.goals()).containsExactly(goal);
    }

    @ParameterizedTest(name = "rejects progress {0}")
    @ValueSource(ints = {-1, 101})
    void rejectsGoalProgressOutsidePercentageRange(int progress) {
        // Act
        Throwable thrown = catchThrowable(() -> new CareGoal(new CareGoalId(UUID.randomUUID()), "Goal",
                null, GoalStatus.IN_PROGRESS, progress));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCarePlanWithoutGoals() {
        // Act
        Throwable thrown = catchThrowable(() -> CarePlan.create(new CarePlanId(UUID.randomUUID()), clinicalCase(),
                "Focus", "Weekly", LocalDate.of(2026, 7, 15), List.of(), NOW));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCarePlanWithDuplicateGoalIds() {
        // Arrange
        CareGoal goal = goal();
        CareGoal duplicate = new CareGoal(goal.id(), "Same id", null, GoalStatus.NOT_STARTED, 0);

        // Act
        Throwable thrown = catchThrowable(() -> CarePlan.create(new CarePlanId(UUID.randomUUID()), clinicalCase(),
                "Focus", "Weekly", LocalDate.of(2026, 7, 15), List.of(goal, duplicate), NOW));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidCarePlans")
    void rejectsCarePlanWithMissingField(String invalid, CarePlanId id, ClinicalCaseId caseId,
                                         PsychologistId psychologistId, PatientId patientId, LocalDate reviewDate,
                                         List<CareGoal> goals, Instant createdAt, Instant updatedAt) {
        // Act
        Throwable thrown = catchThrowable(() -> CarePlan.rehydrate(id, caseId, psychologistId, patientId, "Focus",
                "Weekly", reviewDate, goals, createdAt, updatedAt));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidCarePlanReplacements")
    void rejectsInvalidCarePlanReplacement(String invalid, LocalDate reviewDate, List<CareGoal> goals,
                                           Instant updatedAt) {
        // Arrange
        CarePlan plan = plan(clinicalCase());

        // Act
        Throwable thrown = catchThrowable(() -> plan.replace("Focus", "Weekly", reviewDate, goals, updatedAt));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void completedAppointmentStartsAnUnsignedPendingNote() {
        // Act
        SessionRecord session = pendingSession(clinicalCase().id());

        // Assert
        assertThat(session.noteStatus()).isEqualTo(NoteStatus.PENDING_NOTE);
        assertThat(session.signedAt()).isEmpty();
        assertThat(session.signedBy()).isEmpty();
    }

    @Test
    void editingAPendingNoteMovesItToDraft() {
        // Arrange
        SessionRecord session = pendingSession(clinicalCase().id());

        // Act
        session.update(session.clinicalCaseId().orElseThrow(), NOW.plusSeconds(30), SessionModality.ONLINE,
                55, AttendanceStatus.ATTENDED, NoteStatus.DRAFT, "Summary", "Focus",
                "Intervention", "Homework", "Next", NOW.plusSeconds(60));

        // Assert
        assertThat(session.noteStatus()).isEqualTo(NoteStatus.DRAFT);
        assertThat(session.summary()).isEqualTo("Summary");
    }

    @Test
    void editingADraftCanMoveItBackToPending() {
        // Arrange
        SessionRecord session = session(NoteStatus.DRAFT, "Summary");

        // Act
        session.update(null, NOW, SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED,
                NoteStatus.PENDING_NOTE, "Summary", null, null, null, null, NOW.plusSeconds(10));

        // Assert
        assertThat(session.noteStatus()).isEqualTo(NoteStatus.PENDING_NOTE);
    }

    @Test
    void editsCannotSignANote() {
        // Arrange
        SessionRecord session = session(NoteStatus.DRAFT, "Summary");

        // Act
        Throwable thrown = catchThrowable(() -> session.update(null, NOW, SessionModality.ONLINE, 50,
                AttendanceStatus.ATTENDED, NoteStatus.SIGNED, "Changed", null, null, null, null, NOW.plusSeconds(30)));

        // Assert
        assertThat(thrown).isInstanceOf(InvalidNoteTransitionException.class);
    }

    @Test
    void rejectedSigningEditLeavesTheDraftUnchanged() {
        // Arrange
        SessionRecord session = session(NoteStatus.DRAFT, "Summary");

        // Act
        catchThrowable(() -> session.update(null, NOW, SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED,
                NoteStatus.SIGNED, "Changed", null, null, null, null, NOW.plusSeconds(30)));

        // Assert
        assertThat(session.noteStatus()).isEqualTo(NoteStatus.DRAFT);
        assertThat(session.summary()).isEqualTo("Summary");
        assertThat(session.signedAt()).isEmpty();
        assertThat(session.updatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidSessionUpdates")
    void rejectsInvalidSessionUpdate(String invalid, Instant sessionDate, SessionModality modality, int duration,
                                     AttendanceStatus attendance, NoteStatus noteStatus, Instant updatedAt) {
        // Arrange
        SessionRecord session = pendingSession(null);

        // Act
        Throwable thrown = catchThrowable(() -> session.update(null, sessionDate, modality, duration, attendance,
                noteStatus, null, null, null, null, null, updatedAt));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void signingReturnsTheSameNote() {
        // Arrange
        SessionRecord session = session(NoteStatus.DRAFT, "Summary");

        // Act
        SessionRecord signed = session.sign(PSYCHOLOGIST_ID, NOW.plusSeconds(120));

        // Assert
        assertThat(signed).isSameAs(session);
    }

    @Test
    void signingRecordsTheSignatureAndKeepsTheContent() {
        // Arrange
        SessionRecord session = session(NoteStatus.DRAFT, "Summary");

        // Act
        session.sign(PSYCHOLOGIST_ID, NOW.plusSeconds(120));

        // Assert
        assertThat(session.noteStatus()).isEqualTo(NoteStatus.SIGNED);
        assertThat(session.signedAt()).contains(NOW.plusSeconds(120));
        assertThat(session.signedBy()).contains(PSYCHOLOGIST_ID);
        assertThat(session.updatedAt()).isEqualTo(NOW.plusSeconds(120));
        assertThat(session.summary()).isEqualTo("Summary");
    }

    @Test
    void signingAtTheLastUpdateInstantIsAllowed() {
        // Arrange
        SessionRecord draft = session(NoteStatus.DRAFT, "Summary");

        // Act
        draft.sign(PSYCHOLOGIST_ID, NOW);

        // Assert
        assertThat(draft.signedAt()).contains(NOW);
    }

    @ParameterizedTest(name = "rejects signing a {0}")
    @MethodSource("notSignableNotes")
    void rejectsSigningANoteThatIsNotASummarizedDraft(String note, SessionRecord session) {
        // Act
        Throwable thrown = catchThrowable(() -> session.sign(PSYCHOLOGIST_ID, NOW));

        // Assert
        assertThat(thrown).isInstanceOf(SessionRecordNotSignableException.class);
    }

    @ParameterizedTest(name = "{0} keeps its status")
    @MethodSource("notSignableNotes")
    void rejectedSigningKeepsTheNoteStatus(String note, SessionRecord session) {
        // Arrange
        NoteStatus before = session.noteStatus();

        // Act
        catchThrowable(() -> session.sign(PSYCHOLOGIST_ID, NOW));

        // Assert
        assertThat(session.noteStatus()).isEqualTo(before);
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidSignatures")
    void rejectsInvalidSignature(String invalid, PsychologistId signer, Instant signedAt) {
        // Arrange
        SessionRecord draft = session(NoteStatus.DRAFT, "Summary");

        // Act
        Throwable thrown = catchThrowable(() -> draft.sign(signer, signedAt));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0} leaves the draft unsigned")
    @MethodSource("invalidSignatures")
    void rejectedSignatureLeavesTheDraftUnsigned(String invalid, PsychologistId signer, Instant signedAt) {
        // Arrange
        SessionRecord draft = session(NoteStatus.DRAFT, "Summary");

        // Act
        catchThrowable(() -> draft.sign(signer, signedAt));

        // Assert
        assertThat(draft.noteStatus()).isEqualTo(NoteStatus.DRAFT);
        assertThat(draft.signedAt()).isEmpty();
        assertThat(draft.signedBy()).isEmpty();
    }

    @ParameterizedTest(name = "rejects edit to {0}")
    @EnumSource(NoteStatus.class)
    void signedNotesRejectEdits(NoteStatus target) {
        // Arrange
        SessionRecord session = signedSession();

        // Act
        Throwable thrown = catchThrowable(() -> session.update(null, NOW, SessionModality.IN_PERSON, 30,
                AttendanceStatus.NO_SHOW, target, "Rewritten", "Focus", null, null, null, NOW.plusSeconds(120)));

        // Assert
        assertThat(thrown).isInstanceOf(SignedSessionRecordException.class);
    }

    @Test
    void signedNotesRejectEditsBeforeValidatingThem() {
        // Arrange
        SessionRecord session = signedSession();

        // Act
        Throwable thrown = catchThrowable(() -> session.update(null, null, null, 30, null, null, null, null, null,
                null, null, null));

        // Assert
        assertThat(thrown).isInstanceOf(SignedSessionRecordException.class);
    }

    @Test
    void signedNotesRejectResigning() {
        // Arrange
        SessionRecord session = signedSession();

        // Act
        Throwable thrown = catchThrowable(() -> session.sign(PSYCHOLOGIST_ID, NOW.plusSeconds(120)));

        // Assert
        assertThat(thrown).isInstanceOf(SignedSessionRecordException.class);
    }

    @Test
    void rejectedEditLeavesTheSignedNoteUnchanged() {
        // Arrange
        SessionRecord session = signedSession();

        // Act
        catchThrowable(() -> session.update(null, NOW, SessionModality.IN_PERSON, 30, AttendanceStatus.NO_SHOW,
                NoteStatus.DRAFT, "Rewritten", "Focus", null, null, null, NOW.plusSeconds(120)));

        // Assert
        assertThat(session.noteStatus()).isEqualTo(NoteStatus.SIGNED);
        assertThat(session.summary()).isEqualTo("Summary");
        assertThat(session.modality()).isEqualTo(SessionModality.ONLINE);
        assertThat(session.attendanceStatus()).isEqualTo(AttendanceStatus.ATTENDED);
        assertThat(session.durationMinutes()).isEqualTo(50);
        assertThat(session.clinicalCaseId()).isPresent();
        assertThat(session.signedAt()).contains(NOW.plusSeconds(60));
        assertThat(session.updatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @ParameterizedTest(name = "{0} with signedAt={1} and signedBy={2}")
    @MethodSource("inconsistentSignatures")
    void rehydrationRejectsSignatureInconsistentWithNoteStatus(NoteStatus noteStatus, Instant signedAt,
                                                               PsychologistId signedBy) {
        // Act
        Throwable thrown = catchThrowable(() -> SessionRecord.rehydrate(new SessionRecordId(UUID.randomUUID()),
                null, PSYCHOLOGIST_ID, PATIENT_ID, null, NOW, SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED,
                noteStatus, "Summary", null, null, null, null, NOW, NOW, signedAt, signedBy));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rehydrationKeepsTheSignatureOfSignedNotes() {
        // Act
        SessionRecord signed = SessionRecord.rehydrate(new SessionRecordId(UUID.randomUUID()), null,
                PSYCHOLOGIST_ID, PATIENT_ID, null, NOW, SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED,
                NoteStatus.SIGNED, "Summary", null, null, null, null, NOW, NOW, NOW, PSYCHOLOGIST_ID);

        // Assert
        assertThat(signed.signedBy()).contains(PSYCHOLOGIST_ID);
    }

    @ParameterizedTest(name = "rejects duration {0}")
    @ValueSource(ints = {-1, 481})
    void rejectsSessionDurationOutsideBounds(int duration) {
        // Act
        Throwable thrown = catchThrowable(() -> SessionRecord.fromCompletedAppointment(
                new SessionRecordId(UUID.randomUUID()), null, PSYCHOLOGIST_ID, PATIENT_ID, null, NOW,
                SessionModality.ONLINE, duration, NOW));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidSessionRecords")
    void rejectsSessionRecordWithMissingField(String invalid, SessionRecordId id, PsychologistId psychologistId,
                                              PatientId patientId, Instant sessionDate, SessionModality modality,
                                              AttendanceStatus attendance, NoteStatus noteStatus, Instant createdAt,
                                              Instant updatedAt) {
        // Act
        Throwable thrown = catchThrowable(() -> SessionRecord.rehydrate(id, null, psychologistId, patientId, null,
                sessionDate, modality, 50, attendance, noteStatus, null, null, null, null, null, createdAt,
                updatedAt, null, null));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordOfTheAppointmentsPsychologistAndPatientBelongsToTheAppointment() {
        // Arrange
        SessionRecord session = pendingSession(null);

        // Act
        boolean recordOfOwner = session.isRecordOf(PSYCHOLOGIST_ID, PATIENT_ID);

        // Assert
        assertThat(recordOfOwner).isTrue();
    }

    @ParameterizedTest(name = "appointment of {0}")
    @MethodSource("appointmentOwnersOtherThanTheRecord")
    void recordOfAnotherPsychologistOrPatientDoesNotBelongToTheAppointment(String owner,
                                                                          PsychologistId appointmentPsychologist,
                                                                          PatientId appointmentPatient) {
        // Arrange
        SessionRecord session = pendingSession(null);

        // Act
        boolean recordOfOwner = session.isRecordOf(appointmentPsychologist, appointmentPatient);

        // Assert
        assertThat(recordOfOwner).isFalse();
    }

    @ParameterizedTest(name = "appointment of {0}")
    @MethodSource("appointmentOwnersOtherThanTheRecord")
    void releasingAConflictingLinkClearsTheAppointment(String owner, PsychologistId appointmentPsychologist,
                                                       PatientId appointmentPatient) {
        // Arrange
        SessionRecord session = pendingSession(null);

        // Act
        session.releaseAppointmentLink(new SessionRecordLinkConflictId(UUID.randomUUID()), appointmentPsychologist,
                appointmentPatient, LinkConflictSource.APPOINTMENT_COMPLETED, NOW.plusSeconds(60));

        // Assert
        assertThat(session.appointmentId()).isEmpty();
    }

    @Test
    void releasingAConflictingLinkDescribesTheConflict() {
        // Arrange
        SessionRecord session = pendingSession(null);
        AppointmentId appointmentId = session.appointmentId().orElseThrow();
        SessionRecordLinkConflictId conflictId = new SessionRecordLinkConflictId(UUID.randomUUID());

        // Act
        SessionRecordLinkConflict conflict = session.releaseAppointmentLink(conflictId, OTHER_PSYCHOLOGIST_ID,
                OTHER_PATIENT_ID, LinkConflictSource.MANUAL_LINK, NOW.plusSeconds(60));

        // Assert
        assertThat(conflict).isEqualTo(new SessionRecordLinkConflict(conflictId, session.id(), appointmentId,
                OTHER_PSYCHOLOGIST_ID, OTHER_PATIENT_ID, LinkConflictSource.MANUAL_LINK, NOW.plusSeconds(60)));
    }

    @Test
    void releasingAConflictingLinkKeepsASignedNoteAsSigned() {
        // Arrange
        SessionRecord session = SessionRecord.rehydrate(new SessionRecordId(UUID.randomUUID()), null,
                PSYCHOLOGIST_ID, PATIENT_ID, new AppointmentId(UUID.randomUUID()), NOW, SessionModality.ONLINE, 50,
                AttendanceStatus.ATTENDED, NoteStatus.SIGNED, "Summary", "Focus", null, null, null, NOW,
                NOW.plusSeconds(60), NOW.plusSeconds(60), PSYCHOLOGIST_ID);

        // Act
        session.releaseAppointmentLink(new SessionRecordLinkConflictId(UUID.randomUUID()), OTHER_PSYCHOLOGIST_ID,
                OTHER_PATIENT_ID, LinkConflictSource.APPOINTMENT_COMPLETED, NOW.plusSeconds(120));

        // Assert
        assertThat(session.noteStatus()).isEqualTo(NoteStatus.SIGNED);
        assertThat(session.summary()).isEqualTo("Summary");
        assertThat(session.signedAt()).contains(NOW.plusSeconds(60));
        assertThat(session.signedBy()).contains(PSYCHOLOGIST_ID);
        assertThat(session.updatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void rejectsReleasingTheLinkOfTheAppointmentOwnersRecord() {
        // Arrange
        SessionRecord session = pendingSession(null);

        // Act
        Throwable thrown = catchThrowable(() -> session.releaseAppointmentLink(
                new SessionRecordLinkConflictId(UUID.randomUUID()), PSYCHOLOGIST_ID, PATIENT_ID,
                LinkConflictSource.APPOINTMENT_COMPLETED, NOW));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectedReleaseKeepsTheOwnersLink() {
        // Arrange
        SessionRecord session = pendingSession(null);
        AppointmentId appointmentId = session.appointmentId().orElseThrow();

        // Act
        catchThrowable(() -> session.releaseAppointmentLink(new SessionRecordLinkConflictId(UUID.randomUUID()),
                PSYCHOLOGIST_ID, PATIENT_ID, LinkConflictSource.APPOINTMENT_COMPLETED, NOW));

        // Assert
        assertThat(session.appointmentId()).contains(appointmentId);
    }

    @Test
    void rejectsReleasingALinkFromARecordWithoutAppointment() {
        // Arrange
        SessionRecord session = session(NoteStatus.DRAFT, "Summary");

        // Act
        Throwable thrown = catchThrowable(() -> session.releaseAppointmentLink(
                new SessionRecordLinkConflictId(UUID.randomUUID()), OTHER_PSYCHOLOGIST_ID, OTHER_PATIENT_ID,
                LinkConflictSource.MANUAL_LINK, NOW));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectedReleaseWithIncompleteAuditKeepsTheLink() {
        // Arrange
        SessionRecord session = pendingSession(null);
        AppointmentId appointmentId = session.appointmentId().orElseThrow();

        // Act
        Throwable thrown = catchThrowable(() -> session.releaseAppointmentLink(null, OTHER_PSYCHOLOGIST_ID,
                OTHER_PATIENT_ID, LinkConflictSource.MANUAL_LINK, NOW));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
        assertThat(session.appointmentId()).contains(appointmentId);
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidLinkConflicts")
    void rejectsLinkConflictWithMissingField(String invalid, SessionRecordLinkConflictId id,
                                             SessionRecordId sessionRecordId, AppointmentId appointmentId,
                                             PsychologistId appointmentPsychologist, PatientId appointmentPatient,
                                             LinkConflictSource source, Instant detectedAt) {
        // Act
        Throwable thrown = catchThrowable(() -> new SessionRecordLinkConflict(id, sessionRecordId, appointmentId,
                appointmentPsychologist, appointmentPatient, source, detectedAt));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "summary [{0}]")
    @NullSource
    @ValueSource(strings = "  ")
    void manualSessionWithoutSummaryStartsPending(String summary) {
        // Act
        SessionRecord session = SessionRecord.manual(new SessionRecordId(UUID.randomUUID()), clinicalCase(), null,
                NOW, SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED, summary, null, null, null, null, NOW);

        // Assert
        assertThat(session.noteStatus()).isEqualTo(NoteStatus.PENDING_NOTE);
    }

    @Test
    void clinicalCaseExposesItsState() {
        // Act
        ClinicalCase clinicalCase = clinicalCase();

        // Assert
        assertThat(clinicalCase.psychologistId()).isEqualTo(PSYCHOLOGIST_ID);
        assertThat(clinicalCase.patientId()).isEqualTo(PATIENT_ID);
        assertThat(clinicalCase.presentingConcern()).isEqualTo("Anxiety symptoms affecting work");
        assertThat(clinicalCase.openedAt()).isEqualTo(NOW);
        assertThat(clinicalCase.createdAt()).isEqualTo(NOW);
        assertThat(clinicalCase.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void practiceProfileExposesItsState() {
        // Act
        PracticeProfile profile = practiceProfile();

        // Assert
        assertThat(profile.psychologistId()).isEqualTo(PSYCHOLOGIST_ID);
        assertThat(profile.timezone()).isEqualTo(ZoneId.of("Europe/Lisbon"));
        assertThat(profile.defaultSessionDurationMinutes()).isEqualTo(50);
        assertThat(profile.primaryLocation()).isEqualTo("Lisbon");
        assertThat(profile.telehealthEnabled()).isTrue();
        assertThat(profile.createdAt()).isEqualTo(NOW);
        assertThat(profile.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void careGoalExposesItsState() {
        // Arrange
        CareGoalId goalId = new CareGoalId(UUID.randomUUID());

        // Act
        CareGoal goal = new CareGoal(goalId, "Goal", LocalDate.of(2026, 7, 1), GoalStatus.IN_PROGRESS, 40);

        // Assert
        assertThat(goal.id()).isEqualTo(goalId);
        assertThat(goal.description()).isEqualTo("Goal");
        assertThat(goal.targetDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(goal.status()).isEqualTo(GoalStatus.IN_PROGRESS);
        assertThat(goal.progressPercentage()).isEqualTo(40);
    }

    @Test
    void carePlanExposesItsState() {
        // Arrange
        ClinicalCase clinicalCase = clinicalCase();
        CarePlanId planId = new CarePlanId(UUID.randomUUID());
        CareGoal goal = goal();

        // Act
        CarePlan plan = CarePlan.create(planId, clinicalCase, "Focus", "Weekly", LocalDate.of(2026, 7, 15),
                List.of(goal), NOW);

        // Assert
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
    }

    @Test
    void sessionRecordExposesItsState() {
        // Arrange
        SessionRecordId sessionId = new SessionRecordId(UUID.randomUUID());
        ClinicalCaseId caseId = new ClinicalCaseId(UUID.randomUUID());
        AppointmentId appointmentId = new AppointmentId(UUID.randomUUID());

        // Act
        SessionRecord session = SessionRecord.rehydrate(sessionId, caseId, PSYCHOLOGIST_ID, PATIENT_ID,
                appointmentId, NOW, SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED, NoteStatus.SIGNED,
                "Summary", "Focus", "CBT", "Homework", "Next", NOW, NOW.plusSeconds(1), NOW.plusSeconds(1),
                PSYCHOLOGIST_ID);

        // Assert
        assertThat(session.id()).isEqualTo(sessionId);
        assertThat(session.clinicalCaseId()).contains(caseId);
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
        assertThat(session.signedAt()).contains(NOW.plusSeconds(1));
        assertThat(session.signedBy()).contains(PSYCHOLOGIST_ID);
    }

    @ParameterizedTest(name = "{0} equality follows identity")
    @MethodSource("sameIdentityPairs")
    void aggregatesWithTheSameIdentityAreEqual(String aggregate, Object instance, Object sameIdentity) {
        // Act
        boolean equal = instance.equals(sameIdentity);

        // Assert
        assertThat(equal).isTrue();
        assertThat(instance).hasSameHashCodeAs(sameIdentity);
    }

    @ParameterizedTest(name = "{0} differs from another identity")
    @MethodSource("differentIdentityPairs")
    void aggregatesWithDifferentIdentitiesAreNotEqual(String aggregate, Object instance, Object otherIdentity) {
        // Act
        boolean equal = instance.equals(otherIdentity);

        // Assert
        assertThat(equal).isFalse();
    }

    @ParameterizedTest(name = "{0} equals itself")
    @MethodSource("aggregates")
    void aggregatesEqualThemselves(String aggregate, Object instance) {
        // Act
        boolean equal = instance.equals(instance);

        // Assert
        assertThat(equal).isTrue();
    }

    @ParameterizedTest(name = "{0} differs from other types")
    @MethodSource("aggregates")
    void aggregatesAreNotEqualToOtherTypes(String aggregate, Object instance) {
        // Act
        boolean equal = instance.equals(new Object());

        // Assert
        assertThat(equal).isFalse();
    }

    @Test
    void requiredTextIsTrimmed() {
        // Act
        String value = TextFields.required(" value ", 5, "invalid");

        // Assert
        assertThat(value).isEqualTo("value");
    }

    @ParameterizedTest(name = "optional [{0}] becomes null")
    @NullSource
    @ValueSource(strings = "  ")
    void optionalBlankTextBecomesNull(String text) {
        // Act
        String value = TextFields.optional(text, 5, "invalid");

        // Assert
        assertThat(value).isNull();
    }

    @ParameterizedTest(name = "required [{0}] is rejected")
    @NullSource
    @ValueSource(strings = " ")
    void requiredTextRejectsBlankValues(String text) {
        // Act
        Throwable thrown = catchThrowable(() -> TextFields.required(text, 5, "invalid"));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void optionalTextRejectsValuesAboveMaximumLength() {
        // Act
        Throwable thrown = catchThrowable(() -> TextFields.optional("123456", 5, "invalid"));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0} rejects null")
    @MethodSource("nullIdentifiers")
    void identifiersRejectNullValues(String identifier, ThrowingCallable build) {
        // Act
        Throwable thrown = catchThrowable(build);

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    private static PracticeProfile practiceProfile() {
        return PracticeProfile.create(PSYCHOLOGIST_ID, "OPP 123", ZoneId.of("Europe/Lisbon"), 50, "Lisbon", true, NOW);
    }

    private static SessionRecord pendingSession(ClinicalCaseId caseId) {
        return SessionRecord.fromCompletedAppointment(new SessionRecordId(UUID.randomUUID()), caseId,
                PSYCHOLOGIST_ID, PATIENT_ID, new AppointmentId(UUID.randomUUID()), NOW, SessionModality.ONLINE, 50,
                NOW);
    }

    private static SessionRecord session(NoteStatus noteStatus, String summary) {
        return SessionRecord.rehydrate(new SessionRecordId(UUID.randomUUID()), new ClinicalCaseId(UUID.randomUUID()),
                PSYCHOLOGIST_ID, PATIENT_ID, null, NOW, SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED,
                noteStatus, summary, null, null, null, null, NOW, NOW, null, null);
    }

    private static SessionRecord signedSession() {
        return session(NoteStatus.DRAFT, "Summary").sign(PSYCHOLOGIST_ID, NOW.plusSeconds(60));
    }

    private static CarePlan plan(ClinicalCase clinicalCase) {
        return CarePlan.create(new CarePlanId(UUID.randomUUID()), clinicalCase, "Focus", "Weekly",
                LocalDate.of(2026, 7, 15), List.of(goal()), NOW);
    }

    private static CareGoal goal() {
        return new CareGoal(new CareGoalId(UUID.randomUUID()), "Goal", null, GoalStatus.NOT_STARTED, 0);
    }

    private static ClinicalCase clinicalCase() {
        return ClinicalCase.create(new ClinicalCaseId(UUID.randomUUID()), PSYCHOLOGIST_ID, PATIENT_ID,
                "Anxiety symptoms affecting work", NOW);
    }
}
