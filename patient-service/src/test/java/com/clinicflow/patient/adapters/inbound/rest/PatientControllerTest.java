package com.clinicflow.patient.adapters.inbound.rest;

import com.clinicflow.patient.application.PatientService;
import com.clinicflow.patient.application.commands.UpdatePatientCommand;
import com.clinicflow.patient.application.exceptions.PatientNotFoundException;
import com.clinicflow.patient.application.exceptions.StaleResourceVersionException;
import com.clinicflow.patient.application.results.PatientResult;
import com.clinicflow.patient.domain.ConsentStatus;
import com.clinicflow.patient.domain.ContactPreference;
import com.clinicflow.patient.domain.PatientStatus;
import com.clinicflow.patient.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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

@WebMvcTest(PatientController.class)
@Import({SecurityConfig.class, PatientRestMapperImpl.class})
class PatientControllerTest {
    private static final UUID PSYCHOLOGIST_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PATIENT_ID =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final long VERSION = 7;
    private static final String UPDATE_BODY = """
            {
              "fullName": "Updated Name",
              "preferredName": null,
              "birthDate": null,
              "phone": null,
              "emergencyContactName": null,
              "emergencyContactPhone": null,
              "emergencyContactRelationship": null,
              "contactPreference": "PHONE",
              "consentStatus": "REVOKED"
            }
            """;

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private PatientService patientService;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        // Arrange
        var request = get("/patients");

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsPatientRole() throws Exception {
        // Arrange
        var request = get("/patients").with(jwtFor("ROLE_PATIENT"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isForbidden());
    }

    @Test
    void createReturnsCreatedPatient() throws Exception {
        // Arrange
        when(patientService.create(any())).thenReturn(result());
        var request = post("/patients")
                .with(jwtFor("ROLE_PSYCHOLOGIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "patient@example.com",
                          "fullName": "Patient Name",
                          "preferredName": "Pat",
                          "birthDate": "1990-01-01",
                          "phone": "+351912345678",
                          "emergencyContactName": "Emergency One",
                          "emergencyContactPhone": "+351923456789",
                          "emergencyContactRelationship": "Partner",
                          "contactPreference": "EMAIL",
                          "consentStatus": "GRANTED"
                        }
                        """);

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(PATIENT_ID.toString()));
    }

    @Test
    void createReturnsTheVersionAsEntityTag() throws Exception {
        // Arrange
        when(patientService.create(any())).thenReturn(result());
        var request = post("/patients")
                .with(jwtFor("ROLE_PSYCHOLOGIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "patient@example.com", "fullName": "Patient Name", "consentStatus": "GRANTED"}
                        """);

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(header().string("ETag", "\"7\""))
                .andExpect(jsonPath("$.version").value(VERSION));
    }

    @Test
    void listReturnsPsychologistPatients() throws Exception {
        // Arrange
        when(patientService.list(any())).thenReturn(List.of(result()));
        var request = get("/patients").with(jwtFor("ROLE_PSYCHOLOGIST"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(PATIENT_ID.toString()))
                .andExpect(jsonPath("$[0].version").value(VERSION));
    }

    @Test
    void getReturnsRequestedPatient() throws Exception {
        // Arrange
        when(patientService.get(any())).thenReturn(result());
        var request = get("/patients/{id}", PATIENT_ID).with(jwtFor("ROLE_PSYCHOLOGIST"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"7\""));
    }

    @Test
    void updateReturnsUpdatedPatient() throws Exception {
        // Arrange
        when(patientService.update(any())).thenReturn(result());
        var request = update("\"6\"");

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"7\""));
    }

    @Test
    void updatePassesTheIfMatchVersionToTheService() throws Exception {
        // Arrange
        when(patientService.update(any())).thenReturn(result());
        var request = update("\"6\"");

        // Act
        mvc.perform(request);

        // Assert
        ArgumentCaptor<UpdatePatientCommand> command = ArgumentCaptor.forClass(UpdatePatientCommand.class);
        verify(patientService).update(command.capture());
        assertThat(command.getValue().expectedVersion()).isEqualTo(6);
    }

    @Test
    void updateWithoutIfMatchIsPreconditionRequired() throws Exception {
        // Arrange
        var request = update(null);

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("precondition_required"));
    }

    @Test
    void updateWithoutIfMatchNeverReachesTheService() throws Exception {
        // Arrange
        var request = update(null);

        // Act
        mvc.perform(request);

        // Assert
        verifyNoInteractions(patientService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "6", "W/\"6\"", "\"6\", \"7\"", "\"-1\"", "\"abc\"", "\"1234567890123456789\""})
    void updateRejectsAnIfMatchThatIsNotOneVersionTag(String ifMatch) throws Exception {
        // Arrange
        var request = update(ifMatch);

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_precondition"));
    }

    @Test
    void mapsAStaleVersionToPreconditionFailed() throws Exception {
        // Arrange
        when(patientService.update(any())).thenThrow(new StaleResourceVersionException());
        var request = update("\"6\"");

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("stale_resource_version"));
    }

    @Test
    void changeStatusReturnsUpdatedPatient() throws Exception {
        // Arrange
        when(patientService.changeStatus(any())).thenReturn(result());
        var request = patch("/patients/{id}/status", PATIENT_ID)
                .with(jwtFor("ROLE_PSYCHOLOGIST"))
                .header("If-Match", "\"6\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\"}");

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk());
    }

    @Test
    void changeStatusWithoutIfMatchIsPreconditionRequired() throws Exception {
        // Arrange
        var request = patch("/patients/{id}/status", PATIENT_ID)
                .with(jwtFor("ROLE_PSYCHOLOGIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\"}");

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isPreconditionRequired());
    }

    @Test
    void invalidPhoneReturnsGenericBadRequest() throws Exception {
        // Arrange
        var request = post("/patients")
                .with(jwtFor("ROLE_PSYCHOLOGIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "patient@example.com",
                          "fullName": "Patient Name",
                          "phone": "912345678",
                          "contactPreference": "EMAIL",
                          "consentStatus": "GRANTED"
                        }
                        """);

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value("Request validation failed"));
    }

    @Test
    void mapsUnknownOrForeignPatientToNotFoundProblemDetail() throws Exception {
        // Arrange
        when(patientService.get(any())).thenThrow(new PatientNotFoundException());
        var request = get("/patients/{id}", PATIENT_ID).with(jwtFor("ROLE_PSYCHOLOGIST"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("patient_not_found"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder update(String ifMatch) {
        var request = put("/patients/{id}", PATIENT_ID)
                .with(jwtFor("ROLE_PSYCHOLOGIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_BODY);
        return ifMatch == null ? request : request.header("If-Match", ifMatch);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(String authority) {
        return jwt()
                .jwt(token -> token.subject(PSYCHOLOGIST_ID.toString()))
                .authorities(new SimpleGrantedAuthority(authority));
    }

    private PatientResult result() {
        Instant now = Instant.parse("2026-06-01T10:00:00Z");
        return new PatientResult(PATIENT_ID, PSYCHOLOGIST_ID, "patient@example.com", "Patient Name",
                "+351912345678", "Pat", LocalDate.of(1990, 1, 1), "Emergency One",
                "+351923456789", "Partner", ContactPreference.EMAIL, ConsentStatus.GRANTED,
                now, PatientStatus.ACTIVE, now, now, VERSION);
    }
}
