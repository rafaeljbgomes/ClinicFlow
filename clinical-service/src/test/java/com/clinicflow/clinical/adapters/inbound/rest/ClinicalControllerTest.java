package com.clinicflow.clinical.adapters.inbound.rest;

import com.clinicflow.clinical.application.ClinicalService;
import com.clinicflow.clinical.application.exceptions.ActiveClinicalCaseExistsException;
import com.clinicflow.clinical.application.exceptions.AppointmentAlreadyRecordedException;
import com.clinicflow.clinical.application.exceptions.AppointmentNotFoundException;
import com.clinicflow.clinical.application.exceptions.AppointmentPatientMismatchException;
import com.clinicflow.clinical.application.exceptions.OwnershipCheckUnavailableException;
import com.clinicflow.clinical.application.commands.SignSessionRecordCommand;
import com.clinicflow.clinical.application.commands.UpdatePracticeProfileCommand;
import com.clinicflow.clinical.application.exceptions.PatientNotFoundException;
import com.clinicflow.clinical.application.exceptions.StaleResourceVersionException;
import com.clinicflow.clinical.application.results.CareGoalResult;
import com.clinicflow.clinical.application.results.CarePlanResult;
import com.clinicflow.clinical.application.results.ClinicalCaseResult;
import com.clinicflow.clinical.application.results.PatientClinicalHistoryResult;
import com.clinicflow.clinical.application.results.PracticeProfileResult;
import com.clinicflow.clinical.application.results.SessionRecordResult;
import com.clinicflow.clinical.domain.AttendanceStatus;
import com.clinicflow.clinical.domain.ClinicalCaseStatus;
import com.clinicflow.clinical.domain.GoalStatus;
import com.clinicflow.clinical.domain.InvalidNoteTransitionException;
import com.clinicflow.clinical.domain.NoteStatus;
import com.clinicflow.clinical.domain.SessionModality;
import com.clinicflow.clinical.domain.SessionRecordNotSignableException;
import com.clinicflow.clinical.domain.SignedSessionRecordException;
import com.clinicflow.clinical.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PracticeProfileController.class, ClinicalCaseController.class})
@Import({SecurityConfig.class, ClinicalRestMapperImpl.class, ApiExceptionHandler.class})
class ClinicalControllerTest {
    private static final UUID PSYCHOLOGIST_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PATIENT_ID =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CASE_ID =
            UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID SESSION_ID =
            UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final String NEW_CASE = """
            {"patientId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
             "presentingConcern":"Anxiety symptoms"}
            """;
    private static final String SIGNING_UPDATE = """
            {"sessionDate":"2026-06-01T10:00:00Z","modality":"ONLINE","durationMinutes":50,
             "attendanceStatus":"ATTENDED","noteStatus":"SIGNED","summary":"Summary"}
            """;

    @Autowired private MockMvc mvc;
    @MockitoBean private ClinicalService clinicalService;
    @MockitoBean private JwtDecoder jwtDecoder;

    static Stream<Arguments> sessionUpdateLockFailures() {
        return Stream.of(
                Arguments.of(new SignedSessionRecordException(), HttpStatus.CONFLICT, "session_record_signed"),
                Arguments.of(new InvalidNoteTransitionException(), HttpStatus.BAD_REQUEST, "invalid_note_transition"));
    }

    static Stream<Arguments> caseCreationOwnershipFailures() {
        return Stream.of(
                Arguments.of(new PatientNotFoundException(), HttpStatus.NOT_FOUND, "patient_not_found"),
                Arguments.of(new OwnershipCheckUnavailableException("down"), HttpStatus.SERVICE_UNAVAILABLE,
                        "ownership_check_unavailable"));
    }

    static Stream<Arguments> sessionAppointmentLinkFailures() {
        return Stream.of(
                Arguments.of(new AppointmentNotFoundException(), HttpStatus.NOT_FOUND, "appointment_not_found"),
                Arguments.of(new AppointmentPatientMismatchException(), HttpStatus.BAD_REQUEST,
                        "appointment_patient_mismatch"),
                Arguments.of(new AppointmentAlreadyRecordedException(), HttpStatus.CONFLICT,
                        "appointment_already_recorded"),
                Arguments.of(new OwnershipCheckUnavailableException("down"), HttpStatus.SERVICE_UNAVAILABLE,
                        "ownership_check_unavailable"));
    }

    static Stream<Arguments> sessionSignLockFailures() {
        return Stream.of(
                Arguments.of(new SignedSessionRecordException(), HttpStatus.CONFLICT, "session_record_signed"),
                Arguments.of(new SessionRecordNotSignableException(), HttpStatus.CONFLICT,
                        "session_record_not_signable"));
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        // Arrange
        var request = get("/clinical-cases");

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsPatientRole() throws Exception {
        // Arrange
        var request = get("/clinical-cases").with(jwtFor("ROLE_PATIENT"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isForbidden());
    }

    @Test
    void getPracticeProfileReturnsTheProfile() throws Exception {
        // Arrange
        when(clinicalService.getPracticeProfile(any())).thenReturn(profile());
        var request = get("/practice-profile").with(psychologist());

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultSessionDurationMinutes").value(50));
    }

    @Test
    void putPracticeProfileReturnsTheUpdatedProfile() throws Exception {
        // Arrange
        when(clinicalService.updatePracticeProfile(any())).thenReturn(profile());
        var request = put("/practice-profile").with(psychologist())
                .header("If-Match", "\"3\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"professionalRegistration":"OPP 123","timezone":"Europe/Lisbon",
                         "defaultSessionDurationMinutes":50,"primaryLocation":"Lisbon",
                         "telehealthEnabled":true}
                        """);

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.professionalRegistration").value("OPP 123"));
    }

    @Test
    void createClinicalCaseReturnsCreatedCase() throws Exception {
        // Arrange
        when(clinicalService.createClinicalCase(any())).thenReturn(clinicalCase());
        var request = post("/clinical-cases").with(psychologist())
                .contentType(MediaType.APPLICATION_JSON)
                .content(NEW_CASE);

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(CASE_ID.toString()));
    }

    @Test
    void listClinicalCasesReturnsPsychologistCases() throws Exception {
        // Arrange
        when(clinicalService.listClinicalCases(any())).thenReturn(List.of(clinicalCase()));
        var request = get("/clinical-cases").with(psychologist());

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$[0].patientId").value(PATIENT_ID.toString()));
    }

    @Test
    void getClinicalCaseReturnsRequestedCase() throws Exception {
        // Arrange
        when(clinicalService.getClinicalCase(any())).thenReturn(clinicalCase());
        var request = get("/clinical-cases/{id}", CASE_ID).with(psychologist());

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CASE_ID.toString()));
    }

    @Test
    void changeClinicalCaseStatusReturnsTheCase() throws Exception {
        // Arrange
        when(clinicalService.changeClinicalCaseStatus(any())).thenReturn(clinicalCase());
        var request = patch("/clinical-cases/{id}/status", CASE_ID).with(psychologist())
                .header("If-Match", "\"3\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\"}");

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void putCarePlanReturnsPlanWithGoals() throws Exception {
        // Arrange
        when(clinicalService.putCarePlan(any())).thenReturn(carePlan());
        var request = put("/clinical-cases/{id}/care-plan", CASE_ID).with(psychologist())
                .header("If-Match", "\"3\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"therapeuticFocus":"Reduce avoidance","plannedFrequency":"Weekly",
                         "reviewDate":"2026-07-01",
                         "goals":[{"description":"Practice exposure","status":"IN_PROGRESS",
                                   "progressPercentage":25}]}
                        """);

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.goals[0].progressPercentage").value(25));
    }

    @Test
    void getCarePlanReturnsThePlan() throws Exception {
        // Arrange
        when(clinicalService.getCarePlan(any())).thenReturn(carePlan());
        var request = get("/clinical-cases/{id}/care-plan", CASE_ID).with(psychologist());

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.therapeuticFocus").value("Reduce avoidance"));
    }

    @Test
    void createSessionRecordReturnsDraftNote() throws Exception {
        // Arrange
        when(clinicalService.createSessionRecord(any())).thenReturn(session());
        var request = post("/clinical-cases/{id}/session-records", CASE_ID).with(psychologist())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sessionDate":"2026-06-01T10:00:00Z","modality":"ONLINE",
                         "durationMinutes":50,"attendanceStatus":"ATTENDED",
                         "summary":"Session summary"}
                        """);

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isCreated())
                .andExpect(jsonPath("$.noteStatus").value("DRAFT"));
    }

    @Test
    void listSessionRecordsReturnsCaseSessions() throws Exception {
        // Arrange
        when(clinicalService.listSessionRecords(any())).thenReturn(List.of(session()));
        var request = get("/clinical-cases/{id}/session-records", CASE_ID).with(psychologist());

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(SESSION_ID.toString()));
    }

    @Test
    void updateSessionRecordReturnsTheNote() throws Exception {
        // Arrange
        when(clinicalService.updateSessionRecord(any())).thenReturn(session());
        var request = patch("/session-records/{id}", SESSION_ID).with(psychologist())
                .header("If-Match", "\"3\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"clinicalCaseId":"cccccccc-cccc-cccc-cccc-cccccccccccc",
                         "sessionDate":"2026-06-01T10:00:00Z","modality":"ONLINE",
                         "durationMinutes":50,"attendanceStatus":"ATTENDED",
                         "noteStatus":"DRAFT","summary":"Draft summary"}
                        """);

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SESSION_ID.toString()));
    }

    @Test
    void signSessionRecordReturnsTheSignature() throws Exception {
        // Arrange
        when(clinicalService.signSessionRecord(any())).thenReturn(signedSession());
        var request = post("/session-records/{id}/sign", SESSION_ID).with(psychologist())
                .header("If-Match", "\"3\"");

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.noteStatus").value("SIGNED"))
                .andExpect(jsonPath("$.signedBy").value(PSYCHOLOGIST_ID.toString()))
                .andExpect(jsonPath("$.signedAt").value("2026-06-01T11:00:00Z"));
    }

    @Test
    void signSessionRecordReturnsTheNewVersionAsEntityTag() throws Exception {
        // Arrange
        when(clinicalService.signSessionRecord(any())).thenReturn(signedSession());
        var request = signRequest("\"2\"");

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(header().string("ETag", "\"3\""))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void signingPassesTheReviewedVersionToTheService() throws Exception {
        // Arrange
        when(clinicalService.signSessionRecord(any())).thenReturn(signedSession());
        var request = signRequest("\"2\"");

        // Act
        mvc.perform(request);

        // Assert
        ArgumentCaptor<SignSessionRecordCommand> command = ArgumentCaptor.forClass(SignSessionRecordCommand.class);
        verify(clinicalService).signSessionRecord(command.capture());
        assertThat(command.getValue().expectedVersion()).isEqualTo(2);
    }

    @Test
    void signingWithoutIfMatchIsPreconditionRequired() throws Exception {
        // Arrange
        var request = signRequest(null);

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("precondition_required"));
    }

    @Test
    void signingWithoutIfMatchNeverReachesTheService() throws Exception {
        // Arrange
        var request = signRequest(null);

        // Act
        mvc.perform(request);

        // Assert
        verifyNoInteractions(clinicalService);
    }

    @Test
    void signingAStaleRevisionIsPreconditionFailed() throws Exception {
        // Arrange
        when(clinicalService.signSessionRecord(any())).thenThrow(new StaleResourceVersionException());
        var request = signRequest("\"2\"");

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("stale_resource_version"));
    }

    @Test
    void statusChangeWithoutIfMatchIsPreconditionRequired() throws Exception {
        // Arrange
        var request = patch("/clinical-cases/{id}/status", CASE_ID).with(psychologist())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DISCHARGED\"}");

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isPreconditionRequired());
    }

    @Test
    void firstPracticeProfileSaveExpectsNoStoredProfile() throws Exception {
        // Arrange
        when(clinicalService.updatePracticeProfile(any())).thenReturn(profile());
        var request = practiceProfileRequest().header("If-None-Match", "*");

        // Act
        mvc.perform(request);

        // Assert
        ArgumentCaptor<UpdatePracticeProfileCommand> command =
                ArgumentCaptor.forClass(UpdatePracticeProfileCommand.class);
        verify(clinicalService).updatePracticeProfile(command.capture());
        assertThat(command.getValue().expectedVersion()).isNull();
    }

    @Test
    void practiceProfileSaveWithoutAPreconditionIsPreconditionRequired() throws Exception {
        // Arrange
        var request = practiceProfileRequest();

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isPreconditionRequired());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"3\"", "W/\"3\""})
    void practiceProfileSaveRejectsAnIfNoneMatchOtherThanAWildcard(String ifNoneMatch) throws Exception {
        // Arrange
        var request = practiceProfileRequest().header("If-None-Match", ifNoneMatch);

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_precondition"));
    }

    @Test
    void practiceProfileSaveRejectsBothPreconditions() throws Exception {
        // Arrange
        var request = practiceProfileRequest().header("If-None-Match", "*").header("If-Match", "\"3\"");

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_precondition"));
    }

    @Test
    void unsavedPracticeProfileHasNoEntityTag() throws Exception {
        // Arrange
        when(clinicalService.getPracticeProfile(any())).thenReturn(new PracticeProfileResult(PSYCHOLOGIST_ID, null,
                "UTC", 50, null, false, NOW, NOW, null));
        var request = get("/practice-profile").with(psychologist());

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(header().doesNotExist("ETag"))
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void storedPracticeProfileHasItsVersionAsEntityTag() throws Exception {
        // Arrange
        when(clinicalService.getPracticeProfile(any())).thenReturn(profile());
        var request = get("/practice-profile").with(psychologist());

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(header().string("ETag", "\"3\""));
    }

    @Test
    void patientClinicalHistoryReturnsPatientCases() throws Exception {
        // Arrange
        when(clinicalService.getPatientClinicalHistory(any())).thenReturn(history());
        var request = get("/patients/{id}/clinical-history", PATIENT_ID).with(psychologist());

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.cases[0].id").value(CASE_ID.toString()));
    }

    @Test
    void mapsActiveCaseConflictToProblemDetail() throws Exception {
        // Arrange
        when(clinicalService.createClinicalCase(any())).thenThrow(new ActiveClinicalCaseExistsException());
        var request = post("/clinical-cases").with(psychologist())
                .contentType(MediaType.APPLICATION_JSON)
                .content(NEW_CASE);

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Active clinical case exists"));
    }

    @ParameterizedTest(name = "case creation failure {0} maps to {2}")
    @MethodSource("caseCreationOwnershipFailures")
    void mapsCaseCreationOwnershipFailureToStableProblemCode(RuntimeException failure, HttpStatus expectedStatus,
                                                             String expectedCode) throws Exception {
        // Arrange
        when(clinicalService.createClinicalCase(any())).thenThrow(failure);
        var request = post("/clinical-cases").with(psychologist())
                .contentType(MediaType.APPLICATION_JSON)
                .content(NEW_CASE);

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().is(expectedStatus.value()))
                .andExpect(jsonPath("$.code").value(expectedCode));
    }

    @ParameterizedTest(name = "appointment link failure {0} maps to {2}")
    @MethodSource("sessionAppointmentLinkFailures")
    void mapsSessionAppointmentLinkFailureToStableProblemCode(RuntimeException failure, HttpStatus expectedStatus,
                                                              String expectedCode) throws Exception {
        // Arrange
        when(clinicalService.createSessionRecord(any())).thenThrow(failure);
        var request = post("/clinical-cases/{id}/session-records", CASE_ID).with(psychologist())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"appointmentId":"dddddddd-dddd-dddd-dddd-dddddddddddd",
                         "sessionDate":"2026-06-01T10:00:00Z","modality":"ONLINE",
                         "durationMinutes":50,"attendanceStatus":"ATTENDED"}
                        """);

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().is(expectedStatus.value()))
                .andExpect(jsonPath("$.code").value(expectedCode));
    }

    @ParameterizedTest(name = "update failure {0} maps to {2}")
    @MethodSource("sessionUpdateLockFailures")
    void mapsSessionUpdateLockFailureToStableProblemCode(RuntimeException failure, HttpStatus expectedStatus,
                                                         String expectedCode) throws Exception {
        // Arrange
        when(clinicalService.updateSessionRecord(any())).thenThrow(failure);
        var request = patch("/session-records/{id}", SESSION_ID).with(psychologist())
                .header("If-Match", "\"3\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SIGNING_UPDATE);

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().is(expectedStatus.value()))
                .andExpect(jsonPath("$.code").value(expectedCode));
    }

    @ParameterizedTest(name = "sign failure {0} maps to {2}")
    @MethodSource("sessionSignLockFailures")
    void mapsSessionSignLockFailureToStableProblemCode(RuntimeException failure, HttpStatus expectedStatus,
                                                       String expectedCode) throws Exception {
        // Arrange
        when(clinicalService.signSessionRecord(any())).thenThrow(failure);
        var request = post("/session-records/{id}/sign", SESSION_ID).with(psychologist())
                .header("If-Match", "\"3\"");

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().is(expectedStatus.value()))
                .andExpect(jsonPath("$.code").value(expectedCode));
    }

    @Test
    void rejectsPatientRoleOnSessionSigning() throws Exception {
        // Arrange
        var request = post("/session-records/{id}/sign", SESSION_ID).with(jwtFor("ROLE_PATIENT"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isForbidden());
    }

    private RequestPostProcessor psychologist() {
        return jwtFor("ROLE_PSYCHOLOGIST");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signRequest(String ifMatch) {
        var request = post("/session-records/{id}/sign", SESSION_ID).with(psychologist());
        return ifMatch == null ? request : request.header("If-Match", ifMatch);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder practiceProfileRequest() {
        return put("/practice-profile").with(psychologist())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"professionalRegistration":"OPP 123","timezone":"Europe/Lisbon",
                         "defaultSessionDurationMinutes":50,"primaryLocation":"Lisbon",
                         "telehealthEnabled":true}
                        """);
    }

    private RequestPostProcessor jwtFor(String authority) {
        return jwt()
                .jwt(token -> token.claim("userId", PSYCHOLOGIST_ID.toString()))
                .authorities(new SimpleGrantedAuthority(authority));
    }

    private PracticeProfileResult profile() {
        return new PracticeProfileResult(PSYCHOLOGIST_ID, "OPP 123", "Europe/Lisbon",
                50, "Lisbon", true, NOW, NOW, 3L);
    }

    private ClinicalCaseResult clinicalCase() {
        return new ClinicalCaseResult(CASE_ID, PSYCHOLOGIST_ID, PATIENT_ID, "Anxiety symptoms",
                ClinicalCaseStatus.ACTIVE, NOW, null, NOW, NOW, 3);
    }

    private CarePlanResult carePlan() {
        return new CarePlanResult(UUID.randomUUID(), CASE_ID, PSYCHOLOGIST_ID, PATIENT_ID,
                "Reduce avoidance", "Weekly", LocalDate.of(2026, 7, 1),
                List.of(new CareGoalResult(UUID.randomUUID(), "Practice exposure",
                        null, GoalStatus.IN_PROGRESS, 25)), NOW, NOW, 3);
    }

    private SessionRecordResult session() {
        return new SessionRecordResult(SESSION_ID, CASE_ID, PSYCHOLOGIST_ID, PATIENT_ID, null,
                NOW, SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED, NoteStatus.DRAFT,
                "Session summary", null, null, null, null, NOW, NOW, null, null, 3);
    }

    private SessionRecordResult signedSession() {
        Instant signedAt = Instant.parse("2026-06-01T11:00:00Z");
        return new SessionRecordResult(SESSION_ID, CASE_ID, PSYCHOLOGIST_ID, PATIENT_ID, null,
                NOW, SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED, NoteStatus.SIGNED,
                "Session summary", null, null, null, null, NOW, signedAt, signedAt, PSYCHOLOGIST_ID, 3);
    }

    private PatientClinicalHistoryResult history() {
        return new PatientClinicalHistoryResult(PATIENT_ID, List.of(clinicalCase()), List.of(carePlan()),
                List.of(session()));
    }
}
