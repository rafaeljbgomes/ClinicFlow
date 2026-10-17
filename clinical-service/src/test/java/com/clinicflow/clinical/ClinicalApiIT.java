package com.clinicflow.clinical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

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
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
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
    void runsTheCoreClinicalWorkflowAcrossHttpServicesAndRepositories() {
        var profile = exchange("/practice-profile", HttpMethod.PUT, Map.of(
                "professionalRegistration", "OPP 12345",
                "timezone", "Europe/Lisbon",
                "defaultSessionDurationMinutes", 50,
                "primaryLocation", "Lisbon",
                "telehealthEnabled", true));
        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);

        var clinicalCase = exchange("/clinical-cases", HttpMethod.POST, Map.of(
                "patientId", PATIENT_ID.toString(),
                "presentingConcern", "Anxiety and avoidance"));
        assertThat(clinicalCase.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String caseId = clinicalCase.getBody().get("id").toString();

        var plan = exchange("/clinical-cases/" + caseId + "/care-plan", HttpMethod.PUT, Map.of(
                "therapeuticFocus", "Reduce avoidance",
                "plannedFrequency", "Weekly",
                "reviewDate", LocalDate.now().plusMonths(1).toString(),
                "goals", List.of(Map.of(
                        "description", "Complete graded exposure",
                        "status", "IN_PROGRESS",
                        "progressPercentage", 20))));
        assertThat(plan.getStatusCode()).isEqualTo(HttpStatus.OK);

        var session = exchange("/clinical-cases/" + caseId + "/session-records", HttpMethod.POST, Map.of(
                "sessionDate", Instant.now().toString(),
                "modality", "ONLINE",
                "durationMinutes", 50,
                "attendanceStatus", "ATTENDED",
                "summary", "Initial formulation"));
        assertThat(session.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(session.getBody()).containsEntry("noteStatus", "DRAFT");

        var history = exchange("/patients/" + PATIENT_ID + "/clinical-history", HttpMethod.GET, null);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) history.getBody().get("cases")).hasSize(1);
        assertThat((List<?>) history.getBody().get("carePlans")).hasSize(1);
        assertThat((List<?>) history.getBody().get("sessionRecords")).hasSize(1);
    }

    @Test
    void enforcesActiveCaseConflictValidationAuthenticationAndRole() {
        Map<String, Object> request = Map.of(
                "patientId", PATIENT_ID.toString(), "presentingConcern", "First concern");
        assertThat(exchange("/clinical-cases", HttpMethod.POST, request).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(exchange("/clinical-cases", HttpMethod.POST, request).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exchange("/practice-profile", HttpMethod.PUT, Map.of(
                "timezone", "", "defaultSessionDurationMinutes", 5, "telehealthEnabled", false)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.getForEntity("/clinical-cases", Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var forbidden = http.exchange("/clinical-cases", HttpMethod.GET,
                new HttpEntity<>(bearer("patient-token")), Map.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsCareGoalIdsOwnedByAnotherPsychologistsPlan() {
        String ownerCaseId = createCase("psychologist-token", PATIENT_ID);
        var ownerPlan = putCarePlan("psychologist-token", ownerCaseId, List.of(Map.of(
                "description", "Owner goal", "status", "IN_PROGRESS", "progressPercentage", 30)));
        assertThat(ownerPlan.getStatusCode()).isEqualTo(HttpStatus.OK);
        String ownerPlanId = ownerPlan.getBody().get("id").toString();
        String ownerGoalId = goalIds(ownerPlan.getBody()).getFirst();

        String attackerCaseId = createCase("other-psychologist-token", OTHER_PATIENT_ID);
        Map<String, Object> hijack = Map.of("id", ownerGoalId, "description", "Hijacked goal",
                "status", "ACHIEVED", "progressPercentage", 100);
        assertThat(putCarePlan("other-psychologist-token", attackerCaseId, List.of(hijack)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(putCarePlan("other-psychologist-token", attackerCaseId, List.of(Map.of(
                "description", "Attacker goal", "status", "NOT_STARTED", "progressPercentage", 0)))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(putCarePlan("other-psychologist-token", attackerCaseId, List.of(hijack)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        Map<String, Object> storedGoal = jdbc.queryForMap(
                "SELECT care_plan_id, description, progress_percentage FROM care_goals WHERE id = ?::uuid",
                ownerGoalId);
        assertThat(storedGoal.get("care_plan_id").toString()).isEqualTo(ownerPlanId);
        assertThat(storedGoal).containsEntry("description", "Owner goal").containsEntry("progress_percentage", 30);

        var ownerUpdate = putCarePlan("psychologist-token", ownerCaseId, List.of(
                Map.of("id", ownerGoalId, "description", "Owner goal", "status", "IN_PROGRESS",
                        "progressPercentage", 60),
                Map.of("description", "Second goal", "status", "NOT_STARTED", "progressPercentage", 0)));
        assertThat(ownerUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(goalIds(ownerUpdate.getBody())).hasSize(2).first().isEqualTo(ownerGoalId);

        Map<String, Object> duplicate = Map.of("id", ownerGoalId, "description", "Owner goal",
                "status", "IN_PROGRESS", "progressPercentage", 60);
        assertThat(putCarePlan("psychologist-token", ownerCaseId, List.of(duplicate, duplicate)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void signsDraftNotesOnceThroughTheSignActionAndLocksThem() {
        String caseId = createCase("psychologist-token", PATIENT_ID);
        String sessionId = createSession(caseId, "Initial formulation");

        var signedThroughEdit = exchange("/session-records/" + sessionId, HttpMethod.PATCH,
                sessionUpdate("SIGNED", "Initial formulation"));
        assertProblem(signedThroughEdit, HttpStatus.BAD_REQUEST, "invalid_note_transition");

        var foreignSign = http.exchange("/session-records/" + sessionId + "/sign", HttpMethod.POST,
                new HttpEntity<>(null, bearer("other-psychologist-token")), Map.class);
        assertThat(foreignSign.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(storedSession(sessionId)).containsEntry("note_status", "DRAFT").containsEntry("signed_at", null);

        var signed = exchange("/session-records/" + sessionId + "/sign", HttpMethod.POST, null);
        assertThat(signed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(signed.getBody()).containsEntry("noteStatus", "SIGNED")
                .containsEntry("signedBy", PSYCHOLOGIST_ID.toString());
        assertThat(signed.getBody().get("signedAt")).isNotNull();
        Map<String, Object> signedRow = storedSession(sessionId);
        assertThat(signedRow).containsEntry("note_status", "SIGNED").containsEntry("signed_by", PSYCHOLOGIST_ID);

        assertProblem(exchange("/session-records/" + sessionId + "/sign", HttpMethod.POST, null),
                HttpStatus.CONFLICT, "session_record_signed");
        assertProblem(exchange("/session-records/" + sessionId, HttpMethod.PATCH,
                sessionUpdate("DRAFT", "Rewritten after signing")), HttpStatus.CONFLICT, "session_record_signed");
        assertProblem(exchange("/session-records/" + sessionId, HttpMethod.PATCH,
                sessionUpdate("PENDING_NOTE", "Initial formulation")), HttpStatus.CONFLICT, "session_record_signed");
        assertThat(storedSession(sessionId)).isEqualTo(signedRow);

        var history = exchange("/patients/" + PATIENT_ID + "/clinical-history", HttpMethod.GET, null);
        Map<?, ?> listed = (Map<?, ?>) ((List<?>) history.getBody().get("sessionRecords")).getFirst();
        assertThat(listed.get("noteStatus")).isEqualTo("SIGNED");
        assertThat(listed.get("signedBy")).isEqualTo(PSYCHOLOGIST_ID.toString());
    }

    @Test
    void rejectsSigningNotesThatAreNotDraftsWithASummary() {
        String caseId = createCase("psychologist-token", PATIENT_ID);
        String pendingId = createSession(caseId, null);
        assertProblem(exchange("/session-records/" + pendingId + "/sign", HttpMethod.POST, null),
                HttpStatus.CONFLICT, "session_record_not_signable");

        assertThat(exchange("/session-records/" + pendingId, HttpMethod.PATCH, sessionUpdate("DRAFT", null))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertProblem(exchange("/session-records/" + pendingId + "/sign", HttpMethod.POST, null),
                HttpStatus.CONFLICT, "session_record_not_signable");
        assertThat(storedSession(pendingId)).containsEntry("note_status", "DRAFT").containsEntry("signed_at", null);

        assertThat(exchange("/session-records/" + UUID.randomUUID() + "/sign", HttpMethod.POST, null)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String createSession(String caseId, String summary) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "sessionDate", Instant.now().toString(),
                "modality", "ONLINE",
                "durationMinutes", 50,
                "attendanceStatus", "ATTENDED"));
        if (summary != null) {
            body.put("summary", summary);
        }
        var session = exchange("/clinical-cases/" + caseId + "/session-records", HttpMethod.POST, body);
        assertThat(session.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return session.getBody().get("id").toString();
    }

    private Map<String, Object> sessionUpdate(String noteStatus, String summary) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of(
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

    private void assertProblem(org.springframework.http.ResponseEntity<Map> response, HttpStatus status,
                               String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).containsEntry("code", code);
    }

    private String createCase(String token, UUID patientId) {
        var response = http.exchange("/clinical-cases", HttpMethod.POST, new HttpEntity<>(Map.of(
                "patientId", patientId.toString(), "presentingConcern", "Anxiety and avoidance"),
                bearer(token)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").toString();
    }

    private org.springframework.http.ResponseEntity<Map> putCarePlan(String token, String caseId,
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

    private org.springframework.http.ResponseEntity<Map> exchange(String path, HttpMethod method, Object body) {
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
