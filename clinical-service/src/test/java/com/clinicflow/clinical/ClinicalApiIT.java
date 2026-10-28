package com.clinicflow.clinical;

import com.clinicflow.clinical.application.ports.SessionRecordRepository;
import com.clinicflow.clinical.support.StubHttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@AutoConfigureRestTestClient
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
    private static final UUID SECOND_PATIENT_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    private static final UUID UNVERIFIABLE_PATIENT_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID APPOINTMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND_PATIENT_APPOINTMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    // Real HTTP stand-ins for patient-service and appointment-service; each answers per caller token.
    private static final StubHttpServer PATIENT_SERVICE = StubHttpServer.start();
    private static final StubHttpServer APPOINTMENT_SERVICE = StubHttpServer.start();

    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void neighbourServices(DynamicPropertyRegistry registry) {
        registry.add("clinicflow.services.patient.base-url", PATIENT_SERVICE::baseUrl);
        registry.add("clinicflow.services.appointment.base-url", APPOINTMENT_SERVICE::baseUrl);
    }

    @AfterAll
    static void stopNeighbourServices() {
        PATIENT_SERVICE.close();
        APPOINTMENT_SERVICE.close();
    }

    @Autowired private RestTestClient http;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private JwtDecoder jwtDecoder;
    // Calls the real repository unless a test makes one save fail.
    @MockitoSpyBean private SessionRecordRepository sessionRecords;

    @BeforeEach
    void cleanDatabaseAndConfigureSecurity() {
        jdbc.execute("TRUNCATE TABLE session_records, care_goals, care_plans, clinical_cases, practice_profiles CASCADE");
        reset(jwtDecoder);
        when(jwtDecoder.decode("psychologist-token")).thenReturn(jwt("psychologist-token", "PSYCHOLOGIST", PSYCHOLOGIST_ID));
        when(jwtDecoder.decode("patient-token")).thenReturn(jwt("patient-token", "PATIENT", PSYCHOLOGIST_ID));
        when(jwtDecoder.decode("other-psychologist-token"))
                .thenReturn(jwt("other-psychologist-token", "PSYCHOLOGIST", OTHER_PSYCHOLOGIST_ID));
        arrangeNeighbourServices();
    }

    @Test
    void caseCreationAsksPatientServiceWithTheCallerToken() {
        // Arrange
        Map<String, Object> request = Map.of(
                "patientId", PATIENT_ID.toString(), "presentingConcern", "Anxiety and avoidance");

        // Act
        exchange("/clinical-cases", HttpMethod.POST, request);

        // Assert
        assertThat(PATIENT_SERVICE.requests()).singleElement().satisfies(sent -> {
            assertThat(sent.method()).isEqualTo("HEAD");
            assertThat(sent.path()).isEqualTo("/patients/" + PATIENT_ID);
            assertThat(sent.authorization()).isEqualTo("Bearer psychologist-token");
        });
    }

    @Test
    void rejectsClinicalCaseForAnotherPsychologistsPatient() {
        // Arrange
        Map<String, Object> request = Map.of(
                "patientId", OTHER_PATIENT_ID.toString(), "presentingConcern", "Anxiety and avoidance");

        // Act
        var response = exchange("/clinical-cases", HttpMethod.POST, request);

        // Assert
        assertProblem(response, HttpStatus.NOT_FOUND, "patient_not_found");
    }

    @Test
    void rejectedForeignPatientCaseStoresNothing() {
        // Arrange
        Map<String, Object> request = Map.of(
                "patientId", OTHER_PATIENT_ID.toString(), "presentingConcern", "Anxiety and avoidance");

        // Act
        exchange("/clinical-cases", HttpMethod.POST, request);

        // Assert
        assertThat(jdbc.queryForObject("SELECT count(*) FROM clinical_cases", Long.class)).isZero();
    }

    @Test
    void caseCreationFailsClosedWhenPatientServiceCannotVerifyOwnership() {
        // Arrange
        Map<String, Object> request = Map.of(
                "patientId", UNVERIFIABLE_PATIENT_ID.toString(), "presentingConcern", "Anxiety and avoidance");

        // Act
        var response = exchange("/clinical-cases", HttpMethod.POST, request);

        // Assert
        assertProblem(response, HttpStatus.SERVICE_UNAVAILABLE, "ownership_check_unavailable");
    }

    @Test
    void linksSessionRecordToTheCallersAppointmentForTheCasePatient() {
        // Arrange
        String caseId = createCase("psychologist-token", PATIENT_ID);

        // Act
        var response = exchange("/clinical-cases/" + caseId + "/session-records", HttpMethod.POST,
                linkedSession(APPOINTMENT_ID));

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getResponseBody()).containsEntry("appointmentId", APPOINTMENT_ID.toString());
    }

    @Test
    void rejectsSessionLinkToAnotherPsychologistsAppointment() {
        // Arrange
        String attackerCaseId = createCase("other-psychologist-token", OTHER_PATIENT_ID);

        // Act
        var response = exchange("/clinical-cases/" + attackerCaseId + "/session-records", HttpMethod.POST,
                new HttpEntity<>(linkedSession(APPOINTMENT_ID), bearer("other-psychologist-token")), Map.class);

        // Assert
        assertProblem(response, HttpStatus.NOT_FOUND, "appointment_not_found");
    }

    @Test
    void foreignAppointmentLinkCannotClaimTheOwnersCompletionRecord() {
        // Arrange
        String attackerCaseId = createCase("other-psychologist-token", OTHER_PATIENT_ID);

        // Act
        exchange("/clinical-cases/" + attackerCaseId + "/session-records", HttpMethod.POST,
                new HttpEntity<>(linkedSession(APPOINTMENT_ID), bearer("other-psychologist-token")), Map.class);

        // Assert
        assertThat(jdbc.queryForObject("SELECT count(*) FROM session_records WHERE appointment_id = ?::uuid",
                Long.class, APPOINTMENT_ID.toString())).isZero();
    }

    @Test
    void rejectsSessionLinkToAppointmentOfAnotherPatient() {
        // Arrange
        String caseId = createCase("psychologist-token", PATIENT_ID);

        // Act
        var response = exchange("/clinical-cases/" + caseId + "/session-records", HttpMethod.POST,
                linkedSession(SECOND_PATIENT_APPOINTMENT_ID));

        // Assert
        assertProblem(response, HttpStatus.BAD_REQUEST, "appointment_patient_mismatch");
    }

    @Test
    void rejectsSecondSessionRecordForTheSameAppointment() {
        // Arrange
        String caseId = createCase("psychologist-token", PATIENT_ID);
        arrangeStep("link first session record", exchange("/clinical-cases/" + caseId + "/session-records",
                HttpMethod.POST, linkedSession(APPOINTMENT_ID)), HttpStatus.CREATED);

        // Act
        var response = exchange("/clinical-cases/" + caseId + "/session-records", HttpMethod.POST,
                linkedSession(APPOINTMENT_ID));

        // Assert
        assertProblem(response, HttpStatus.CONFLICT, "appointment_already_recorded");
    }

    @Test
    void appointmentOwnerLinksAnAppointmentClaimedBeforeLinksWereVerified() {
        // Arrange
        legacySignedClaim(APPOINTMENT_ID);
        String caseId = createCase("psychologist-token", PATIENT_ID);

        // Act
        var response = exchange("/clinical-cases/" + caseId + "/session-records", HttpMethod.POST,
                linkedSession(APPOINTMENT_ID));

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getResponseBody()).containsEntry("appointmentId", APPOINTMENT_ID.toString());
    }

    @Test
    void ownersLinkReleasesTheLegacyClaimAndKeepsItsSignedNote() {
        // Arrange
        UUID claimId = legacySignedClaim(APPOINTMENT_ID);
        String caseId = createCase("psychologist-token", PATIENT_ID);

        // Act
        exchange("/clinical-cases/" + caseId + "/session-records", HttpMethod.POST, linkedSession(APPOINTMENT_ID));

        // Assert
        assertThat(storedSession(claimId.toString()))
                .containsEntry("appointment_id", null)
                .containsEntry("note_status", "SIGNED")
                .containsEntry("summary", "Legacy signed note");
        assertThat(jdbc.queryForMap("SELECT * FROM session_record_link_conflicts WHERE session_record_id = ?",
                claimId))
                .containsEntry("appointment_id", APPOINTMENT_ID)
                .containsEntry("appointment_psychologist_id", PSYCHOLOGIST_ID)
                .containsEntry("appointment_patient_id", PATIENT_ID)
                .containsEntry("source", "MANUAL_LINK");
    }

    @Test
    void foreignCallerCannotReleaseALegacyClaim() {
        // Arrange
        UUID claimId = legacySignedClaim(APPOINTMENT_ID);
        String attackerCaseId = createCase("other-psychologist-token", OTHER_PATIENT_ID);

        // Act
        exchange("/clinical-cases/" + attackerCaseId + "/session-records", HttpMethod.POST,
                new HttpEntity<>(linkedSession(APPOINTMENT_ID), bearer("other-psychologist-token")), Map.class);

        // Assert
        assertThat(storedSession(claimId.toString())).containsEntry("appointment_id", APPOINTMENT_ID);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM session_record_link_conflicts", Long.class)).isZero();
    }

    @Test
    void ownersFailedRecordFailsOnlyAfterTheLegacyClaimWasReleased() {
        // Arrange
        UUID claimId = legacySignedClaim(APPOINTMENT_ID);
        String caseId = createCase("psychologist-token", PATIENT_ID);
        failTheOwnersSessionRecord();

        // Act
        exchange("/clinical-cases/" + caseId + "/session-records", HttpMethod.POST, linkedSession(APPOINTMENT_ID));

        // Assert
        InOrder order = inOrder(sessionRecords);
        order.verify(sessionRecords).save(argThat(record -> record.id().value().equals(claimId)));
        order.verify(sessionRecords).save(argThat(record -> record.psychologistId().value().equals(PSYCHOLOGIST_ID)));
    }

    @Test
    void ownersFailedRecordRollsBackTheReleasedLink() {
        // Arrange
        UUID claimId = legacySignedClaim(APPOINTMENT_ID);
        String caseId = createCase("psychologist-token", PATIENT_ID);
        failTheOwnersSessionRecord();

        // Act
        exchange("/clinical-cases/" + caseId + "/session-records", HttpMethod.POST, linkedSession(APPOINTMENT_ID));

        // Assert
        assertThat(storedSession(claimId.toString())).containsEntry("appointment_id", APPOINTMENT_ID);
    }

    @Test
    void ownersFailedRecordRollsBackTheConflictAudit() {
        // Arrange
        legacySignedClaim(APPOINTMENT_ID);
        String caseId = createCase("psychologist-token", PATIENT_ID);
        failTheOwnersSessionRecord();

        // Act
        exchange("/clinical-cases/" + caseId + "/session-records", HttpMethod.POST, linkedSession(APPOINTMENT_ID));

        // Assert
        assertThat(jdbc.queryForObject("SELECT count(*) FROM session_record_link_conflicts", Long.class)).isZero();
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
        var response = putPracticeProfile(request, "If-None-Match", "*");

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getResponseBody()).containsEntry("professionalRegistration", "OPP 12345")
                .containsEntry("version", 0);
    }

    @Test
    void practiceProfileSaveWithoutAPreconditionIsPreconditionRequired() {
        // Act
        var response = exchange("/practice-profile", HttpMethod.PUT, practiceProfile("OPP 12345"));

        // Assert
        assertProblem(response, HttpStatus.PRECONDITION_REQUIRED, "precondition_required");
    }

    // Two first saves at once: one creates the profile, the other is told it is stale, never a 500.
    @Test
    void concurrentFirstPracticeProfileSavesLetExactlyOneWin() throws Exception {
        // Act
        List<HttpStatus> statuses = concurrently(
                () -> putPracticeProfile(practiceProfile("OPP 11111"), "If-None-Match", "*").getStatus(),
                () -> putPracticeProfile(practiceProfile("OPP 22222"), "If-None-Match", "*").getStatus());

        // Assert
        assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.PRECONDITION_FAILED);
    }

    @Test
    void createsClinicalCase() {
        // Arrange
        Map<String, Object> request = Map.of(
                "patientId", PATIENT_ID.toString(), "presentingConcern", "Anxiety and avoidance");

        // Act
        var response = exchange("/clinical-cases", HttpMethod.POST, request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getResponseBody()).containsKey("id");
    }

    @Test
    void putsCarePlanForOwnedCase() {
        // Arrange
        String caseId = createCase("psychologist-token", PATIENT_ID);

        // Act
        var response = putCarePlan("psychologist-token", caseId, List.of(Map.of(
                "description", "Complete graded exposure", "status", "IN_PROGRESS", "progressPercentage", 20)));

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(goalIds(response.getResponseBody())).hasSize(1);
    }

    @Test
    void stalePlanReplacementKeepsGoalsAddedSinceTheFormWasOpened() {
        // Arrange
        String caseId = createCase("psychologist-token", PATIENT_ID);
        var created = arrangeStep("create plan", putCarePlan("psychologist-token", caseId, List.of(Map.of(
                "description", "First goal", "status", "IN_PROGRESS", "progressPercentage", 10))), HttpStatus.OK);
        Map<String, Object> firstGoal = Map.of("id", goalIds(created.getResponseBody()).getFirst(),
                "description", "First goal", "status", "IN_PROGRESS", "progressPercentage", 10);
        arrangeStep("add a goal in another tab", putCarePlan("psychologist-token", caseId, List.of(firstGoal,
                Map.of("description", "Second goal", "status", "NOT_STARTED", "progressPercentage", 0))),
                HttpStatus.OK);

        // Act
        var response = carePlanPut("psychologist-token", caseId, List.of(firstGoal), "If-Match", "\"0\"");

        // Assert
        assertProblem(response, HttpStatus.PRECONDITION_FAILED, "stale_resource_version");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM care_goals", Integer.class)).isEqualTo(2);
    }

    @Test
    void secondFirstSaveOfACarePlanIsPreconditionFailed() {
        // Arrange
        String caseId = createCase("psychologist-token", PATIENT_ID);
        List<Map<String, Object>> goals = List.of(Map.of(
                "description", "Goal", "status", "IN_PROGRESS", "progressPercentage", 10));
        arrangeStep("create plan", carePlanPut("psychologist-token", caseId, goals, "If-None-Match", "*"),
                HttpStatus.OK);

        // Act
        var response = carePlanPut("psychologist-token", caseId, goals, "If-None-Match", "*");

        // Assert
        assertProblem(response, HttpStatus.PRECONDITION_FAILED, "stale_resource_version");
    }

    @Test
    void caseReadReturnsTheVersionAsEntityTag() {
        // Arrange
        String caseId = createCase("psychologist-token", PATIENT_ID);

        // Act
        var response = exchange("/clinical-cases/" + caseId, HttpMethod.GET, null);

        // Assert
        assertThat(response.getResponseHeaders().getETag()).isEqualTo("\"0\"");
    }

    @Test
    void staleSessionEditIsPreconditionFailedAndKeepsTheNewerText() {
        // Arrange
        String sessionId = createSession(createCase("psychologist-token", PATIENT_ID), "Initial formulation");
        arrangeStep("edit in another tab", editSessionAt(sessionId, "\"0\"", sessionUpdate("DRAFT", "Newer text")),
                HttpStatus.OK);

        // Act
        var response = editSessionAt(sessionId, "\"0\"", sessionUpdate("DRAFT", "Older text"));

        // Assert
        assertProblem(response, HttpStatus.PRECONDITION_FAILED, "stale_resource_version");
        assertThat(storedSession(sessionId)).containsEntry("summary", "Newer text");
    }

    @Test
    void sessionEditWithoutIfMatchIsPreconditionRequired() {
        // Arrange
        String sessionId = createSession(createCase("psychologist-token", PATIENT_ID), "Initial formulation");

        // Act
        var response = exchange("/session-records/" + sessionId, HttpMethod.PATCH,
                sessionUpdate("DRAFT", "Unversioned edit"));

        // Assert
        assertProblem(response, HttpStatus.PRECONDITION_REQUIRED, "precondition_required");
    }

    // The psychologist reviewed revision 0; a newer revision saved elsewhere must not be signed in its place.
    @Test
    void signingAStaleRevisionLeavesTheNoteDraft() {
        // Arrange
        String sessionId = createSession(createCase("psychologist-token", PATIENT_ID), "Reviewed text");
        arrangeStep("edit in another tab", editSessionAt(sessionId, "\"0\"", sessionUpdate("DRAFT", "Unreviewed text")),
                HttpStatus.OK);

        // Act
        var response = signAt("psychologist-token", sessionId, "\"0\"");

        // Assert
        assertProblem(response, HttpStatus.PRECONDITION_FAILED, "stale_resource_version");
        assertThat(storedSession(sessionId)).containsEntry("note_status", "DRAFT").containsEntry("signed_at", null);
    }

    // Signing and editing the same revision at once: one wins, and a signed note always holds the reviewed text.
    @Test
    void concurrentSignAndEditOfTheSameRevisionLetExactlyOneWin() throws Exception {
        // Arrange
        String sessionId = createSession(createCase("psychologist-token", PATIENT_ID), "Reviewed text");

        // Act
        List<HttpStatus> statuses = concurrently(
                () -> signAt("psychologist-token", sessionId, "\"0\"").getStatus(),
                () -> editSessionAt(sessionId, "\"0\"", sessionUpdate("DRAFT", "Unreviewed text")).getStatus());

        // Assert
        assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.PRECONDITION_FAILED);
        Map<String, Object> stored = storedSession(sessionId);
        assertThat(stored.get("summary")).isEqualTo(
                "SIGNED".equals(stored.get("note_status")) ? "Reviewed text" : "Unreviewed text");
    }

    @Test
    void createsDraftSessionRecordWithSummary() {
        // Arrange
        String caseId = createCase("psychologist-token", PATIENT_ID);

        // Act
        var response = exchange("/clinical-cases/" + caseId + "/session-records", HttpMethod.POST,
                newSession("Initial formulation"));

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getResponseBody()).containsEntry("noteStatus", "DRAFT");
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
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getResponseBody().get("cases")).hasSize(1);
        assertThat((List<?>) response.getResponseBody().get("carePlans")).hasSize(1);
        assertThat((List<?>) response.getResponseBody().get("sessionRecords")).hasSize(1);
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
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsInvalidPracticeProfile() {
        // Arrange
        Map<String, Object> invalid = Map.of(
                "timezone", "", "defaultSessionDurationMinutes", 5, "telehealthEnabled", false);

        // Act
        var response = exchange("/practice-profile", HttpMethod.PUT, invalid);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listsOneFilteredPageOfOwnCasesMostRecentlyOpenedFirst() {
        // Arrange
        createCase("psychologist-token", PATIENT_ID);
        String newest = createCase("psychologist-token", SECOND_PATIENT_ID);
        createCase("other-psychologist-token", OTHER_PATIENT_ID);
        var request = new HttpEntity<>(bearer("psychologist-token"));

        // Act
        var response = exchange("/clinical-cases?status=INTAKE&status=ACTIVE&size=1", HttpMethod.GET, request,
                Map.class);

        // Assert
        List<?> content = (List<?>) response.getResponseBody().get("content");
        assertThat(content).singleElement().satisfies(item -> assertThat(((Map<?, ?>) item).get("id"))
                .isEqualTo(newest));
        Map<?, ?> page = (Map<?, ?>) response.getResponseBody().get("page");
        assertThat(List.of(page.get("size"), page.get("number"), page.get("totalElements"), page.get("totalPages")))
                .containsExactly(1, 0, 2, 2);
    }

    @Test
    void rejectsAnOversizedCasePage() {
        // Arrange
        var request = new HttpEntity<>(bearer("psychologist-token"));

        // Act
        var response = exchange("/clinical-cases?size=101", HttpMethod.GET, request, Map.class);

        // Assert
        assertProblem(response, HttpStatus.BAD_REQUEST, "invalid_request");
    }

    @Test
    void rejectsMissingAuthentication() {
        // Act
        var response = exchange("/clinical-cases", HttpMethod.GET, HttpEntity.EMPTY, Map.class);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsPatientRole() {
        // Arrange
        var request = new HttpEntity<>(bearer("patient-token"));

        // Act
        var response = exchange("/clinical-cases", HttpMethod.GET, request, Map.class);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsAnotherPsychologistsGoalIdWhenCreatingAPlan() {
        // Arrange
        String ownerGoalId = goalIds(ownerPlan().getResponseBody()).getFirst();
        String attackerCaseId = createCase("other-psychologist-token", OTHER_PATIENT_ID);

        // Act
        var response = putCarePlan("other-psychologist-token", attackerCaseId, List.of(hijack(ownerGoalId)));

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsAnotherPsychologistsGoalIdWhenReplacingAPlan() {
        // Arrange
        String ownerGoalId = goalIds(ownerPlan().getResponseBody()).getFirst();
        String attackerCaseId = createCase("other-psychologist-token", OTHER_PATIENT_ID);
        arrangeStep("put attacker plan", putCarePlan("other-psychologist-token", attackerCaseId, List.of(Map.of(
                "description", "Attacker goal", "status", "NOT_STARTED", "progressPercentage", 0))), HttpStatus.OK);

        // Act
        var response = putCarePlan("other-psychologist-token", attackerCaseId, List.of(hijack(ownerGoalId)));

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectedGoalHijackLeavesTheOwnersGoalUntouched() {
        // Arrange
        var ownerPlan = ownerPlan();
        String ownerPlanId = ownerPlan.getResponseBody().get("id").toString();
        String ownerGoalId = goalIds(ownerPlan.getResponseBody()).getFirst();
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
        String ownerCaseId = ownerPlan.getResponseBody().get("clinicalCaseId").toString();
        String ownerGoalId = goalIds(ownerPlan.getResponseBody()).getFirst();

        // Act
        var response = putCarePlan("psychologist-token", ownerCaseId, List.of(
                Map.of("id", ownerGoalId, "description", "Owner goal", "status", "IN_PROGRESS",
                        "progressPercentage", 60),
                Map.of("description", "Second goal", "status", "NOT_STARTED", "progressPercentage", 0)));

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(goalIds(response.getResponseBody())).hasSize(2).first().isEqualTo(ownerGoalId);
    }

    @Test
    void rejectsDuplicateGoalIdsInOnePlan() {
        // Arrange
        var ownerPlan = ownerPlan();
        String ownerCaseId = ownerPlan.getResponseBody().get("clinicalCaseId").toString();
        Map<String, Object> duplicate = Map.of("id", goalIds(ownerPlan.getResponseBody()).getFirst(),
                "description", "Owner goal", "status", "IN_PROGRESS", "progressPercentage", 60);

        // Act
        var response = putCarePlan("psychologist-token", ownerCaseId, List.of(duplicate, duplicate));

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsSigningThroughAnEdit() {
        // Arrange
        String sessionId = createSession(createCase("psychologist-token", PATIENT_ID), "Initial formulation");

        // Act
        var response = editSession(sessionId,
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
        assertProblem(response, HttpStatus.NOT_FOUND, "clinical_resource_not_found");
    }

    @Test
    void anotherPsychologistsCaseLooksExactlyLikeAMissingOne() {
        // Arrange
        String caseId = createCase("psychologist-token", PATIENT_ID);
        var other = new HttpEntity<>(bearer("other-psychologist-token"));
        var missing = exchange("/clinical-cases/" + UUID.randomUUID(), HttpMethod.GET, other, Map.class);

        // Act
        var foreign = exchange("/clinical-cases/" + caseId, HttpMethod.GET, other, Map.class);

        // Assert
        assertThat(foreign.getStatus()).isEqualTo(HttpStatus.NOT_FOUND).isEqualTo(missing.getStatus());
        assertThat(withoutRequestFields(foreign.getResponseBody()))
                .isEqualTo(withoutRequestFields(missing.getResponseBody()));
    }

    @Test
    void anotherPsychologistsNoteLooksExactlyLikeAMissingOne() {
        // Arrange
        String sessionId = createSession(createCase("psychologist-token", PATIENT_ID), "Initial formulation");
        var missing = signAs("other-psychologist-token", UUID.randomUUID().toString());

        // Act
        var foreign = signAs("other-psychologist-token", sessionId);

        // Assert
        assertThat(withoutRequestFields(foreign.getResponseBody()))
                .isEqualTo(withoutRequestFields(missing.getResponseBody()));
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
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getResponseBody()).containsEntry("noteStatus", "SIGNED")
                .containsEntry("signedBy", PSYCHOLOGIST_ID.toString());
        assertThat(response.getResponseBody().get("signedAt")).isNotNull();
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
        var response = editSession(sessionId,
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
        editSession(sessionId, sessionUpdate("DRAFT", "Rewritten after signing"));

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
        Map<?, ?> listed = (Map<?, ?>) ((List<?>) response.getResponseBody().get("sessionRecords")).getFirst();
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
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private EntityExchangeResult<Map> ownerPlan() {
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
        arrangeStep("move note to draft", editSession(pendingId,
                sessionUpdate("DRAFT", null)), HttpStatus.OK);
        return pendingId;
    }

    // Signs the revision that is stored now, like a psychologist who just reviewed it.
    private EntityExchangeResult<Map> signAs(String token, String sessionId) {
        return signAt(token, sessionId, versionTag("session_records", "id", sessionId));
    }

    private EntityExchangeResult<Map> signAt(String token, String sessionId, String ifMatch) {
        HttpHeaders headers = bearer(token);
        headers.setIfMatch(ifMatch);
        return exchange("/session-records/" + sessionId + "/sign", HttpMethod.POST,
                new HttpEntity<>(null, headers), Map.class);
    }

    private EntityExchangeResult<Map> editSession(String sessionId, Map<String, Object> update) {
        return editSessionAt(sessionId, versionTag("session_records", "id", sessionId), update);
    }

    private EntityExchangeResult<Map> editSessionAt(String sessionId, String ifMatch, Map<String, Object> update) {
        HttpHeaders headers = bearer("psychologist-token");
        headers.setIfMatch(ifMatch);
        return exchange("/session-records/" + sessionId, HttpMethod.PATCH, new HttpEntity<>(update, headers),
                Map.class);
    }

    // The stored version as an entity tag; an unknown row gets "0" so the request still reaches the lookup.
    private String versionTag(String table, String column, String id) {
        List<Long> versions = jdbc.queryForList(
                "SELECT version FROM " + table + " WHERE " + column + " = ?::uuid", Long.class, id);
        return "\"" + (versions.isEmpty() ? 0 : versions.getFirst()) + "\"";
    }

    private Map<String, Object> practiceProfile(String registration) {
        return Map.of(
                "professionalRegistration", registration,
                "timezone", "Europe/Lisbon",
                "defaultSessionDurationMinutes", 50,
                "primaryLocation", "Lisbon",
                "telehealthEnabled", true);
    }

    private EntityExchangeResult<Map> putPracticeProfile(Map<String, Object> profile, String header, String value) {
        HttpHeaders headers = bearer("psychologist-token");
        headers.set(header, value);
        return exchange("/practice-profile", HttpMethod.PUT, new HttpEntity<>(profile, headers), Map.class);
    }

    // Starts both requests together, so they race for the same stored version.
    private List<HttpStatus> concurrently(Callable<HttpStatusCode> first, Callable<HttpStatusCode> second)
            throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService clients = Executors.newFixedThreadPool(2);
        try {
            List<Future<HttpStatusCode>> futures = new ArrayList<>();
            for (Callable<HttpStatusCode> request : List.of(first, second)) {
                futures.add(clients.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return request.call();
                }));
            }
            List<HttpStatus> statuses = new ArrayList<>();
            for (Future<HttpStatusCode> future : futures) {
                statuses.add(HttpStatus.valueOf(future.get(30, TimeUnit.SECONDS).value()));
            }
            return statuses;
        } finally {
            clients.shutdownNow();
        }
    }

    private String createSession(String caseId, String summary) {
        var session = arrangeStep("create session record",
                exchange("/clinical-cases/" + caseId + "/session-records", HttpMethod.POST, newSession(summary)),
                HttpStatus.CREATED);
        return session.getResponseBody().get("id").toString();
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

    private Map<String, Object> linkedSession(UUID appointmentId) {
        Map<String, Object> body = newSession("Linked session");
        body.put("appointmentId", appointmentId.toString());
        return body;
    }

    private static void arrangeNeighbourServices() {
        PATIENT_SERVICE.reset();
        APPOINTMENT_SERVICE.reset();
        PATIENT_SERVICE.replyTo("psychologist-token", "HEAD", "/patients/" + PATIENT_ID, 200, "");
        PATIENT_SERVICE.replyTo("psychologist-token", "HEAD", "/patients/" + SECOND_PATIENT_ID, 200, "");
        PATIENT_SERVICE.replyTo("other-psychologist-token", "HEAD", "/patients/" + OTHER_PATIENT_ID, 200, "");
        PATIENT_SERVICE.reply("HEAD", "/patients/" + UNVERIFIABLE_PATIENT_ID, 500);
        APPOINTMENT_SERVICE.replyTo("psychologist-token", "GET", "/appointments/" + APPOINTMENT_ID, 200,
                appointmentJson(APPOINTMENT_ID, PATIENT_ID));
        APPOINTMENT_SERVICE.replyTo("psychologist-token", "GET", "/appointments/" + SECOND_PATIENT_APPOINTMENT_ID,
                200, appointmentJson(SECOND_PATIENT_APPOINTMENT_ID, SECOND_PATIENT_ID));
    }

    private static String appointmentJson(UUID appointmentId, UUID patientId) {
        return """
                {"id":"%s","psychologistId":"%s","patientId":"%s","scheduledAt":"2099-06-12T10:00:00Z",
                 "status":"COMPLETED","type":"ONLINE","cancellationReason":null}
                """.formatted(appointmentId, PSYCHOLOGIST_ID, patientId);
    }

    private static EntityExchangeResult<Map> arrangeStep(String step, EntityExchangeResult<Map> response,
                                                         HttpStatus expected) {
        if (response.getStatus().value() != expected.value()) {
            throw new IllegalStateException(
                    "Arrange step \"" + step + "\" returned " + response.getStatus() + ", expected " + expected);
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

    // The owner's insert fails after the claim's link was released, as a constraint violation on that row would.
    private void failTheOwnersSessionRecord() {
        doThrow(new DataIntegrityViolationException("Forced failure of the owner's session record"))
                .when(sessionRecords)
                .save(argThat(record -> record.psychologistId().value().equals(PSYCHOLOGIST_ID)));
    }

    // Before ADR-021 any psychologist could link any appointment id; this rebuilds such a row, already signed.
    private UUID legacySignedClaim(UUID appointmentId) {
        UUID claimId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO session_records (id, psychologist_id, patient_id, appointment_id, session_date, modality,
                    duration_minutes, attendance_status, note_status, summary, created_at, updated_at, signed_at,
                    signed_by)
                VALUES (?, ?, ?, ?, ?, 'ONLINE', 50, 'ATTENDED', 'SIGNED', 'Legacy signed note', ?, ?, ?, ?)
                """, claimId, OTHER_PSYCHOLOGIST_ID, OTHER_PATIENT_ID, appointmentId, now, now, now, now,
                OTHER_PSYCHOLOGIST_ID);
        return claimId;
    }

    private Map<String, Object> storedSession(String sessionId) {
        return jdbc.queryForMap("SELECT * FROM session_records WHERE id = ?::uuid", sessionId);
    }

    private void assertProblem(EntityExchangeResult<Map> response, HttpStatus status,
                               String code) {
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getResponseBody()).containsEntry("code", code);
    }

    private String createCase(String token, UUID patientId) {
        var response = arrangeStep("create clinical case", exchange("/clinical-cases", HttpMethod.POST,
                new HttpEntity<>(Map.of("patientId", patientId.toString(), "presentingConcern",
                        "Anxiety and avoidance"), bearer(token)), Map.class), HttpStatus.CREATED);
        return response.getResponseBody().get("id").toString();
    }

    // Creates the plan when the case has none, otherwise replaces the stored version.
    private EntityExchangeResult<Map> putCarePlan(String token, String caseId,
                                                                     List<Map<String, Object>> goals) {
        List<Long> versions = jdbc.queryForList(
                "SELECT version FROM care_plans WHERE clinical_case_id = ?::uuid", Long.class, caseId);
        return versions.isEmpty() ? carePlanPut(token, caseId, goals, "If-None-Match", "*")
                : carePlanPut(token, caseId, goals, "If-Match", "\"" + versions.getFirst() + "\"");
    }

    private EntityExchangeResult<Map> carePlanPut(String token, String caseId, List<Map<String, Object>> goals,
                                                  String header, String value) {
        HttpHeaders headers = bearer(token);
        headers.set(header, value);
        return exchange("/clinical-cases/" + caseId + "/care-plan", HttpMethod.PUT, new HttpEntity<>(Map.of(
                "therapeuticFocus", "Reduce avoidance",
                "plannedFrequency", "Weekly",
                "reviewDate", LocalDate.now().plusMonths(1).toString(),
                "goals", goals), headers), Map.class);
    }

    private List<String> goalIds(Map<?, ?> carePlan) {
        return ((List<?>) carePlan.get("goals")).stream()
                .map(goal -> ((Map<?, ?>) goal).get("id").toString())
                .toList();
    }

    private EntityExchangeResult<Map> exchange(String path, HttpMethod method, Object body) {
        return exchange(path, method, new HttpEntity<>(body, bearer("psychologist-token")), Map.class);
    }

    private <T> EntityExchangeResult<T> exchange(String path, HttpMethod method, HttpEntity<?> request, Class<T> type) {
        var spec = http.method(method).uri(path).headers(headers -> headers.addAll(request.getHeaders()));
        return (request.hasBody() ? spec.body(request.getBody()) : spec).exchange().returnResult(type);
    }

    // Everything except the per-request instance and correlation id must match.
    private static Map<?, ?> withoutRequestFields(Map<?, ?> problem) {
        Map<Object, Object> copy = new java.util.HashMap<>(problem);
        copy.remove("instance");
        copy.remove("correlationId");
        return copy;
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
