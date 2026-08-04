package com.clinicflow.clinical.adapters.inbound.rest;

import com.clinicflow.clinical.application.ClinicalService;
import com.clinicflow.clinical.application.exceptions.ActiveClinicalCaseExistsException;
import com.clinicflow.clinical.application.results.CareGoalResult;
import com.clinicflow.clinical.application.results.CarePlanResult;
import com.clinicflow.clinical.application.results.ClinicalCaseResult;
import com.clinicflow.clinical.application.results.PatientClinicalHistoryResult;
import com.clinicflow.clinical.application.results.PracticeProfileResult;
import com.clinicflow.clinical.application.results.SessionRecordResult;
import com.clinicflow.clinical.domain.AttendanceStatus;
import com.clinicflow.clinical.domain.ClinicalCaseStatus;
import com.clinicflow.clinical.domain.GoalStatus;
import com.clinicflow.clinical.domain.NoteStatus;
import com.clinicflow.clinical.domain.SessionModality;
import com.clinicflow.clinical.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Autowired private MockMvc mvc;
    @MockitoBean private ClinicalService clinicalService;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void rejectsMissingOrWrongRole() throws Exception {
        mvc.perform(get("/clinical-cases")).andExpect(status().isUnauthorized());
        mvc.perform(get("/clinical-cases").with(jwtFor("ROLE_PATIENT"))).andExpect(status().isForbidden());
    }

    @Test
    void executesClinicalEndpointsForPsychologist() throws Exception {
        when(clinicalService.getPracticeProfile(any())).thenReturn(profile());
        when(clinicalService.updatePracticeProfile(any())).thenReturn(profile());
        when(clinicalService.createClinicalCase(any())).thenReturn(clinicalCase());
        when(clinicalService.listClinicalCases(any())).thenReturn(List.of(clinicalCase()));
        when(clinicalService.getClinicalCase(any())).thenReturn(clinicalCase());
        when(clinicalService.changeClinicalCaseStatus(any())).thenReturn(clinicalCase());
        when(clinicalService.getCarePlan(any())).thenReturn(carePlan());
        when(clinicalService.putCarePlan(any())).thenReturn(carePlan());
        when(clinicalService.listSessionRecords(any())).thenReturn(List.of(session()));
        when(clinicalService.createSessionRecord(any())).thenReturn(session());
        when(clinicalService.updateSessionRecord(any())).thenReturn(session());
        when(clinicalService.getPatientClinicalHistory(any())).thenReturn(history());

        mvc.perform(get("/practice-profile").with(jwtFor("ROLE_PSYCHOLOGIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultSessionDurationMinutes").value(50));

        mvc.perform(put("/practice-profile").with(jwtFor("ROLE_PSYCHOLOGIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"professionalRegistration":"OPP 123","timezone":"Europe/Lisbon",
                                 "defaultSessionDurationMinutes":50,"primaryLocation":"Lisbon",
                                 "telehealthEnabled":true}
                                """))
                .andExpect(status().isOk());

        mvc.perform(post("/clinical-cases").with(jwtFor("ROLE_PSYCHOLOGIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                                 "presentingConcern":"Anxiety symptoms"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(CASE_ID.toString()));

        mvc.perform(get("/clinical-cases").with(jwtFor("ROLE_PSYCHOLOGIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].patientId").value(PATIENT_ID.toString()));

        mvc.perform(get("/clinical-cases/{id}", CASE_ID).with(jwtFor("ROLE_PSYCHOLOGIST")))
                .andExpect(status().isOk());

        mvc.perform(patch("/clinical-cases/{id}/status", CASE_ID).with(jwtFor("ROLE_PSYCHOLOGIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        mvc.perform(put("/clinical-cases/{id}/care-plan", CASE_ID).with(jwtFor("ROLE_PSYCHOLOGIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"therapeuticFocus":"Reduce avoidance","plannedFrequency":"Weekly",
                                 "reviewDate":"2026-07-01",
                                 "goals":[{"description":"Practice exposure","status":"IN_PROGRESS",
                                           "progressPercentage":25}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goals[0].progressPercentage").value(25));

        mvc.perform(get("/clinical-cases/{id}/care-plan", CASE_ID).with(jwtFor("ROLE_PSYCHOLOGIST")))
                .andExpect(status().isOk());

        mvc.perform(post("/clinical-cases/{id}/session-records", CASE_ID).with(jwtFor("ROLE_PSYCHOLOGIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionDate":"2026-06-01T10:00:00Z","modality":"ONLINE",
                                 "durationMinutes":50,"attendanceStatus":"ATTENDED",
                                 "summary":"Session summary"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.noteStatus").value("DRAFT"));

        mvc.perform(get("/clinical-cases/{id}/session-records", CASE_ID).with(jwtFor("ROLE_PSYCHOLOGIST")))
                .andExpect(status().isOk());

        mvc.perform(patch("/session-records/{id}", SESSION_ID).with(jwtFor("ROLE_PSYCHOLOGIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clinicalCaseId":"cccccccc-cccc-cccc-cccc-cccccccccccc",
                                 "sessionDate":"2026-06-01T10:00:00Z","modality":"ONLINE",
                                 "durationMinutes":50,"attendanceStatus":"ATTENDED",
                                 "noteStatus":"SIGNED","summary":"Signed summary"}
                                """))
                .andExpect(status().isOk());

        mvc.perform(get("/patients/{id}/clinical-history", PATIENT_ID).with(jwtFor("ROLE_PSYCHOLOGIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases[0].id").value(CASE_ID.toString()));
    }

    @Test
    void mapsActiveCaseConflictToProblemDetail() throws Exception {
        when(clinicalService.createClinicalCase(any())).thenThrow(new ActiveClinicalCaseExistsException());

        mvc.perform(post("/clinical-cases").with(jwtFor("ROLE_PSYCHOLOGIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                                 "presentingConcern":"Anxiety symptoms"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Active clinical case exists"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(String authority) {
        return jwt()
                .jwt(token -> token.claim("userId", PSYCHOLOGIST_ID.toString()))
                .authorities(new SimpleGrantedAuthority(authority));
    }

    private PracticeProfileResult profile() {
        return new PracticeProfileResult(PSYCHOLOGIST_ID, "OPP 123", "Europe/Lisbon",
                50, "Lisbon", true, NOW, NOW);
    }

    private ClinicalCaseResult clinicalCase() {
        return new ClinicalCaseResult(CASE_ID, PSYCHOLOGIST_ID, PATIENT_ID, "Anxiety symptoms",
                ClinicalCaseStatus.ACTIVE, NOW, null, NOW, NOW);
    }

    private CarePlanResult carePlan() {
        return new CarePlanResult(UUID.randomUUID(), CASE_ID, PSYCHOLOGIST_ID, PATIENT_ID,
                "Reduce avoidance", "Weekly", LocalDate.of(2026, 7, 1),
                List.of(new CareGoalResult(UUID.randomUUID(), "Practice exposure",
                        null, GoalStatus.IN_PROGRESS, 25)), NOW, NOW);
    }

    private SessionRecordResult session() {
        return new SessionRecordResult(SESSION_ID, CASE_ID, PSYCHOLOGIST_ID, PATIENT_ID, null,
                NOW, SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED, NoteStatus.DRAFT,
                "Session summary", null, null, null, null, NOW, NOW);
    }

    private PatientClinicalHistoryResult history() {
        return new PatientClinicalHistoryResult(PATIENT_ID, List.of(clinicalCase()), List.of(carePlan()),
                List.of(session()));
    }
}
