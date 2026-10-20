package com.clinicflow.clinical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "clinicflow.demo.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@Testcontainers
class ClinicalApiIT {
    private static final UUID PSYCHOLOGIST_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PATIENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OTHER_PSYCHOLOGIST_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID OTHER_PATIENT_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanDatabaseAndConfigureSecurity() {
        jdbc.execute("TRUNCATE TABLE session_records, care_goals, care_plans, clinical_cases, practice_profiles CASCADE");
        reset(jwtDecoder);
        when(jwtDecoder.decode("psychologist-token")).thenReturn(jwt("psychologist-token", "PSYCHOLOGIST", PSYCHOLOGIST_ID));
        when(jwtDecoder.decode("patient-token")).thenReturn(jwt("patient-token", "PATIENT", PSYCHOLOGIST_ID));
        when(jwtDecoder.decode("other-psychologist-token"))
                .thenReturn(jwt("other-psychologist-token", "PSYCHOLOGIST", OTHER_PSYCHOLOGIST_ID));
    }

    @Test
    void putsPracticeProfile() {
        // Arrange
        Map<String, Object> request = Map.of(
                "professionalRegistration", "OPP 12345",
                "timezone", "Europe/Lisbon",
                "defaultSessionDurationMinutes", 50,
                "primaryLocation", "Lisbon",
                "telehealthEnabled", true);

        // Act
        var response = exchange("/practice-profile", HttpMethod.PUT, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("professionalRegistration", "OPP 12345");
    }

    @Test
    void createsClinicalCase() {
        // Arrange
        Map<String, Object> request = Map.of(
                "patientId", PATIENT_ID.toString(), "presentingConcern", "Anxiety and avoidance");

        // Act
        var response = exchange("/clinical-cases", HttpMethod.POST, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("id");
    }

    @Test
    void putsCarePlanForOwnedCase() {
        // Arrange
        String caseId = createCase("psychologist-token", PATIENT_ID);

        // Act
        var response = putCarePlan("psychologist-token", caseId, List.of(Map.of(
                "description", "Complete graded exposure", "status", "IN_PROGRESS", "progressPercentage", 20)));

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(goalIds(response.getBody())).hasSize(1);
    }

    @Test
    void createsDraftSessionRecordWithSummary() {
        // Arrange
        String caseId = createCase("psychologist-token", PATIENT_ID);

        // Act
        var response = exchange("/clinical-cases/" + caseId + "/session-records", HttpMethod.POST,
                newSession("Initial formulation"));

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("noteStatus", "DRAFT");
    }

    @Test
    void clinicalHistoryAssemblesCasesPlansAndSessions() {
        // Arrange
        String caseId = createCase("psychologist-token", PATIENT_ID);
        arrangeStep("put care plan", putCarePlan("psychologist-token", caseId, List.of(Map.of(
                "description", "Complete graded exposure", "status", "IN_PROGRESS", "progressPercentage", 20))),
                HttpStatus.OK);
        createSession(caseId, "Initial formulation");

        // Act
        var response = exchange("/patients/" + PATIENT_ID + "/clinical-history", HttpMethod.GET, null);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody().get("cases")).hasSize(1);
        assertThat((List<?>) response.getBody().get("carePlans")).hasSize(1);
        assertThat((List<?>) response.getBody().get("sessionRecords")).hasSize(1);
    }

    @Test
    void rejectsSecondOpenCaseForThePatient() {
        // Arrange
        createCase("psychologist-token", PATIENT_ID);
        Map<String, Object> request = Map.of(
                "patientId", PATIENT_ID.toString(), "presentingConcern", "Second concern");

        // Act
        var response = exchange("/clinical-cases", HttpMethod.POST, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsInvalidPracticeProfile() {
        // Arrange
        Map<String, Object> invalid = Map.of(
                "timezone", "", "defaultSessionDurationMinutes", 5, "telehealthEnabled", false);

        // Act
        var response = exchange("/practice-profile", HttpMethod.PUT, invalid);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsMissingAuthentication() {
        // Act
        var response = http.getForEntity("/clinical-cases", Map.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsPatientRole() {
        // Arrange
        var request = new HttpEntity<>(bearer("patient-token"));

        // Act
        var response = http.exchange("/clinical-cases", HttpMethod.GET, request, Map.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsAnotherPsychologistsGoalIdWhenCreatingAPlan() {
        // Arrange
        String ownerGoalId = goalIds(ownerPlan().getBody()).getFirst();
        String attackerCaseId = createCase("other-psychologist-token", OTHER_PATIENT_ID);

        // Act
        var response = putCarePlan("other-psychologist-token", attackerCaseId, List.of(hijack(ownerGoalId)));

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsAnotherPsychologistsGoalIdWhenReplacingAPlan() {
        // Arrange
        String ownerGoalId = goalIds(ownerPlan().getBody()).getFirst();
        String attackerCaseId = createCase("other-psychologist-token", OTHER_PATIENT_ID);
        arrangeStep("put attacker plan", putCarePlan("other-psychologist-token", attackerCaseId, List.of(Map.of(
                "description", "Attacker goal", "status", "NOT_STARTED", "progressPercentage", 0))), HttpStatus.OK);

        // Act
        var response = putCarePlan("other-psychologist-token", attackerCaseId, List.of(hijack(ownerGoalId)));

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectedGoalHijackLeavesTheOwnersGoalUntouched() {
        // Arrange
        var ownerPlan = ownerPlan();
        String ownerPlanId = ownerPlan.getBody().get("id").toString();
        String ownerGoalId = goalIds(ownerPlan.getBody()).getFirst();
        String attackerCaseId = createCase("other-psychologist-token", OTHER_PATIENT_ID);

        // Act
        putCarePlan("other-psychologist-token", attackerCaseId, List.of(hijack(ownerGoalId)));

        // Assert
        Map<String, Object> storedGoal = jdbc.queryForMap(
                "SELECT care_plan_id, description, progress_percentage FROM care_goals WHERE id = ?::uuid",
                ownerGoalId);
        assertThat(storedGoal.get("care_plan_id").toString()).isEqualTo(ownerPlanId);
        assertThat(storedGoal).containsEntry("description", "Owner goal").containsEntry("progress_percentage", 30);
    }

    @Test
    void ownerKeepsGoalIdentityWhileAddingGoals() {
        // Arrange
        var ownerPlan = ownerPlan();
        String ownerCaseId = ownerPlan.getBody().get("clinicalCaseId").toString();
        String ownerGoalId = goalIds(ownerPlan.getBody()).getFirst();

        // Act
        var response = putCarePlan("psychologist-token", ownerCaseId, List.of(
                Map.of("id", ownerGoalId, "description", "Owner goal", "status", "IN_PROGRESS",
                        "progressPercentage", 60),
                Map.of("description", "Second goal", "status", "NOT_STARTED", "progressPercentage", 0)));

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(goalIds(response.getBody())).hasSize(2).first().isEqualTo(ownerGoalId);
    }

    @Test
    void rejectsDuplicateGoalIdsInOnePlan() {
        // Arrange
        var ownerPlan = ownerPlan();
        String ownerCaseId = ownerPlan.getBody().get("clinicalCaseId").toString();
        Map<String, Object> duplicate = Map.of("id", goalIds(ownerPlan.getBody()).getFirst(),
                "description", "Owner goal", "status", "IN_PROGRESS", "progressPercentage", 60);

        // Act
        var response = putCarePlan("psychologist-token", ownerCaseId, List.of(duplicate, duplicate));

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsSigningThroughAnEdit() {
        // Arrange
        String sessionId = createSession(createCase("psychologist-token", PATIENT_ID), "Initial formulation");

        // Act
        var response = exchange("/session-records/" + sessionId, HttpMethod.PATCH,
                sessionUpdate("SIGNED", "Initial formulation"));

        // Assert
        assertProblem(response, HttpStatus.BAD_REQUEST, "invalid_note_transition");
    }

    @Test
    void rejectsSigningAnotherPsychologistsNote() {
        // Arrange
        String sessionId = createSession(createCase("psychologist-token", PATIENT_ID), "Initial formulation");

        // Act
        var response = signAs("other-psychologist-token", sessionId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectedForeignSigningLeavesTheNoteDraft() {
        // Arrange
        String sessionId = createSession(createCase("psychologist-token", PATIENT_ID), "Initial formulation");

        // Act
        signAs("other-psychologist-token", sessionId);

        // Assert
        assertThat(storedSession(sessionId)).containsEntry("note_status", "DRAFT").containsEntry("signed_at", null);
    }

    @Test
    void signsDraftNoteThroughTheSignAction() {
        // Arrange
        String sessionId = createSession(createCase("psychologist-token", PATIENT_ID), "Initial formulation");

        // Act
        var response = signAs("psychologist-token", sessionId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("noteStatus", "SIGNED")
                .containsEntry("signedBy", PSYCHOLOGIST_ID.toString());
        assertThat(response.getBody().get("signedAt")).isNotNull();
    }

    @Test
    void signingPersistsTheSignature() {
        // Arrange
        String sessionId = createSession(createCase("psychologist-token", PATIENT_ID), "Initial formulation");

        // Act
        signAs("psychologist-token", sessionId);

        // Assert
        assertThat(storedSession(sessionId)).containsEntry("note_status", "SIGNED")
                .containsEntry("signed_by", PSYCHOLOGIST_ID);
    }

    @Test
    void rejectsSigningASignedNoteAgain() {
        // Arrange
        String sessionId = signedSession();

        // Act
        var response = signAs("psychologist-token", sessionId);

        // Assert
        assertProblem(response, HttpStatus.CONFLICT, "session_record_signed");
    }

    @ParameterizedTest(name = "rejects edit to {0}")
    @CsvSource({"DRAFT, Rewritten after signing", "PENDING_NOTE, Initial formulation"})
    void rejectsEditsToASignedNote(String noteStatus, String summary) {
        // Arrange
        String sessionId = signedSession();

        // Act
        var response = exchange("/session-records/" + sessionId, HttpMethod.PATCH,
                sessionUpdate(noteStatus, summary));

        // Assert
        assertProblem(response, HttpStatus.CONFLICT, "session_record_signed");
    }

    @Test
    void rejectedEditLeavesTheSignedRowUnchanged() {
        // Arrange
        String sessionId = signedSession();
        Map<String, Object> signedRow = storedSession(sessionId);

        // Act
        exchange("/session-records/" + sessionId, HttpMethod.PATCH, sessionUpdate("DRAFT", "Rewritten after signing"));

        // Assert
        assertThat(storedSession(sessionId)).isEqualTo(signedRow);
    }

    @Test
    void clinicalHistoryListsTheSignature() {
        // Arrange
        signedSession();

        // Act
        var response = exchange("/patients/" + PATIENT_ID + "/clinical-history", HttpMethod.GET, null);

        // Assert
        Map<?, ?> listed = (Map<?, ?>) ((List<?>) response.getBody().get("sessionRecords")).getFirst();
        assertThat(listed.get("noteStatus")).isEqualTo("SIGNED");
        assertThat(listed.get("signedBy")).isEqualTo(PSYCHOLOGIST_ID.toString());
    }

    @Test
    void rejectsSigningAPendingNote() {
        // Arrange
        String pendingId = createSession(createCase("psychologist-token", PATIENT_ID), null);

        // Act
        var response = signAs("psychologist-token", pendingId);

        // Assert
        assertProblem(response, HttpStatus.CONFLICT, "session_record_not_signable");
    }

    @Test
    void rejectsSigningADraftWithoutSummary() {
        // Arrange
        String draftId = draftWithoutSummary();

        // Act
        var response = signAs("psychologist-token", draftId);

        // Assert
        assertProblem(response, HttpStatus.CONFLICT, "session_record_not_signable");
    }

    @Test
    void rejectedSigningLeavesTheDraftUnsigned() {
        // Arrange
        String draftId = draftWithoutSummary();

        // Act
        signAs("psychologist-token", draftId);

        // Assert
        assertThat(storedSession(draftId)).containsEntry("note_status", "DRAFT").containsEntry("signed_at", null);
    }

    @Test
    void reportsUnknownNoteOnSigning() {
        // Act
        var response = signAs("psychologist-token", UUID.randomUUID().toString());

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<Map> ownerPlan() {
        String ownerCaseId = createCase("psychologist-token", PATIENT_ID);
        return arrangeStep("put owner plan", putCarePlan("psychologist-token", ownerCaseId, List.of(Map.of(
                "description", "Owner goal", "status", "IN_PROGRESS", "progressPercentage", 30))), HttpStatus.OK);
    }

    private Map<String, Object> hijack(String goalId) {
        return Map.of("id", goalId, "description", "Hijacked goal", "status", "ACHIEVED", "progressPercentage", 100);
    }

    private String signedSession() {
        String sessionId = createSession(createCase("psychologist-token", PATIENT_ID), "Initial formulation");
        arrangeStep("sign note", signAs("psychologist-token", sessionId), HttpStatus.OK);
        return sessionId;
    }

    private String draftWithoutSummary() {
        String pendingId = createSession(createCase("psychologist-token", PATIENT_ID), null);
        arrangeStep("move note to draft", exchange("/session-records/" + pendingId, HttpMethod.PATCH,
                sessionUpdate("DRAFT", null)), HttpStatus.OK);
        return pendingId;
    }

    private ResponseEntity<Map> signAs(String token, String sessionId) {
        return http.exchange("/session-records/" + sessionId + "/sign", HttpMethod.POST,
                new HttpEntity<>(null, bearer(token)), Map.class);
    }

    private String createSession(String caseId, String summary) {
        var session = arrangeStep("create session record",
                exchange("/clinical-cases/" + caseId + "/session-records", HttpMethod.POST, newSession(summary)),
                HttpStatus.CREATED);
        return session.getBody().get("id").toString();
    }

    private Map<String, Object> newSession(String summary) {
        Map<String, Object> body = new HashMap<>(Map.of(
                "sessionDate", Instant.now().toString(),
                "modality", "ONLINE",
                "durationMinutes", 50,
                "attendanceStatus", "ATTENDED"));
        if (summary != null) {
            body.put("summary", summary);
        }
        return body;
    }

    private static ResponseEntity<Map> arrangeStep(String step, ResponseEntity<Map> response, HttpStatus expected) {
        if (response.getStatusCode().value() != expected.value()) {
            throw new IllegalStateException(
                    "Arrange step \"" + step + "\" returned " + response.getStatusCode() + ", expected " + expected);
        }
        return response;
    }

    private Map<String, Object> sessionUpdate(String noteStatus, String summary) {
        Map<String, Object> body = new HashMap<>(Map.of(
                "sessionDate", Instant.now().toString(),
                "modality", "IN_PERSON",
                "durationMinutes", 45,
                "attendanceStatus", "ATTENDED",
                "noteStatus", noteStatus));
        if (summary != null) {
            body.put("summary", summary);
        }
        return body;
    }

    private Map<String, Object> storedSession(String sessionId) {
        return jdbc.queryForMap("SELECT * FROM session_records WHERE id = ?::uuid", sessionId);
    }

    private void assertProblem(ResponseEntity<Map> response, HttpStatus status,
                               String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).containsEntry("code", code);
    }

    private String createCase(String token, UUID patientId) {
        var response = arrangeStep("create clinical case", http.exchange("/clinical-cases", HttpMethod.POST,
                new HttpEntity<>(Map.of("patientId", patientId.toString(), "presentingConcern",
                        "Anxiety and avoidance"), bearer(token)), Map.class), HttpStatus.CREATED);
        return response.getBody().get("id").toString();
    }

    private ResponseEntity<Map> putCarePlan(String token, String caseId,
                                                                     List<Map<String, Object>> goals) {
        return http.exchange("/clinical-cases/" + caseId + "/care-plan", HttpMethod.PUT, new HttpEntity<>(Map.of(
                "therapeuticFocus", "Reduce avoidance",
                "plannedFrequency", "Weekly",
                "reviewDate", LocalDate.now().plusMonths(1).toString(),
                "goals", goals), bearer(token)), Map.class);
    }

    private List<String> goalIds(Map<?, ?> carePlan) {
        return ((List<?>) carePlan.get("goals")).stream()
                .map(goal -> ((Map<?, ?>) goal).get("id").toString())
                .toList();
    }

    private ResponseEntity<Map> exchange(String path, HttpMethod method, Object body) {
        return http.exchange(path, method, new HttpEntity<>(body, bearer("psychologist-token")), Map.class);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private Jwt jwt(String token, String role, UUID userId) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(token)
                .header("alg", "none")
                .issuedAt(now).expiresAt(now.plusSeconds(3600))
                .claim("userId", userId.toString())
                .claim("role", role)
                .build();
    }
}
