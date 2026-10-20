package com.clinicflow.appointment.adapters.inbound.rest;

import com.clinicflow.appointment.application.AppointmentService;
import com.clinicflow.appointment.application.exceptions.AppointmentAccessDeniedException;
import com.clinicflow.appointment.application.exceptions.OwnershipCheckUnavailableException;
import com.clinicflow.appointment.application.exceptions.PatientNotFoundException;
import com.clinicflow.appointment.application.results.AppointmentResult;
import com.clinicflow.appointment.domain.AppointmentStatus;
import com.clinicflow.appointment.domain.AppointmentType;
import com.clinicflow.appointment.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppointmentController.class)
@Import({SecurityConfig.class, AppointmentRestMapperImpl.class})
class AppointmentControllerTest {
    private static final UUID PSYCHOLOGIST_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID APPOINTMENT_ID =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PATIENT_ID =
            UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final Instant APPOINTMENT_DATE = Instant.parse("2099-06-12T10:00:00Z");

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private AppointmentService appointmentService;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        // Arrange
        var request = get("/appointments");

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsPatientRole() throws Exception {
        // Arrange
        var request = get("/appointments").with(jwtFor("ROLE_PATIENT"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isForbidden());
    }

    @Test
    void scheduleReturnsCreatedAppointment() throws Exception {
        // Arrange
        when(appointmentService.schedule(any())).thenReturn(result());
        var request = post("/appointments")
                .with(jwtFor("ROLE_PSYCHOLOGIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "patientId": "%s",
                          "scheduledAt": "%s",
                          "type": "ONLINE"
                        }
                        """.formatted(PATIENT_ID, APPOINTMENT_DATE));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(APPOINTMENT_ID.toString()));
    }

    @Test
    void scheduleForUnownedPatientReturnsPatientNotFound() throws Exception {
        // Arrange
        when(appointmentService.schedule(any())).thenThrow(new PatientNotFoundException());
        var request = scheduleRequest();

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("patient_not_found"));
    }

    @Test
    void scheduleReturnsServiceUnavailableWhenOwnershipCannotBeVerified() throws Exception {
        // Arrange
        when(appointmentService.schedule(any())).thenThrow(new OwnershipCheckUnavailableException("down"));
        var request = scheduleRequest();

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ownership_check_unavailable"));
    }

    @Test
    void listReturnsPsychologistAppointments() throws Exception {
        // Arrange
        when(appointmentService.listForPsychologist(any())).thenReturn(List.of(result()));
        var request = get("/appointments").with(jwtFor("ROLE_PSYCHOLOGIST"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(APPOINTMENT_ID.toString()));
    }

    @Test
    void getReturnsRequestedAppointment() throws Exception {
        // Arrange
        when(appointmentService.get(any())).thenReturn(result());
        var request = get("/appointments/{id}", APPOINTMENT_ID).with(jwtFor("ROLE_PSYCHOLOGIST"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk());
    }

    @Test
    void rescheduleReturnsUpdatedAppointment() throws Exception {
        // Arrange
        when(appointmentService.reschedule(any())).thenReturn(result());
        var request = patch("/appointments/{id}/reschedule", APPOINTMENT_ID)
                .with(jwtFor("ROLE_PSYCHOLOGIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newDate\":\"2099-06-13T10:00:00Z\"}");

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk());
    }

    @Test
    void cancelReturnsUpdatedAppointment() throws Exception {
        // Arrange
        when(appointmentService.cancel(any())).thenReturn(result());
        var request = patch("/appointments/{id}/cancel", APPOINTMENT_ID)
                .with(jwtFor("ROLE_PSYCHOLOGIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Patient requested cancellation\"}");

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk());
    }

    @Test
    void completeReturnsUpdatedAppointment() throws Exception {
        // Arrange
        when(appointmentService.complete(any())).thenReturn(result());
        var request = patch("/appointments/{id}/complete", APPOINTMENT_ID)
                .with(jwtFor("ROLE_PSYCHOLOGIST"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk());
    }

    @Test
    void rejectsPastAppointmentDate() throws Exception {
        // Arrange
        var request = post("/appointments")
                .with(jwtFor("ROLE_PSYCHOLOGIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "patientId": "%s",
                          "scheduledAt": "2020-01-01T10:00:00Z",
                          "type": "ONLINE"
                        }
                        """.formatted(PATIENT_ID));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void mapsOwnershipFailureToForbiddenProblemDetail() throws Exception {
        // Arrange
        when(appointmentService.get(any())).thenThrow(new AppointmentAccessDeniedException());
        var request = get("/appointments/{id}", APPOINTMENT_ID).with(jwtFor("ROLE_PSYCHOLOGIST"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access denied"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(String authority) {
        return jwt()
                .jwt(token -> token.claim("userId", PSYCHOLOGIST_ID.toString()))
                .authorities(new SimpleGrantedAuthority(authority));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder scheduleRequest() {
        return post("/appointments")
                .with(jwtFor("ROLE_PSYCHOLOGIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "patientId": "%s",
                          "scheduledAt": "%s",
                          "type": "ONLINE"
                        }
                        """.formatted(PATIENT_ID, APPOINTMENT_DATE));
    }

    private AppointmentResult result() {
        return new AppointmentResult(APPOINTMENT_ID, PSYCHOLOGIST_ID, PATIENT_ID, APPOINTMENT_DATE,
                AppointmentStatus.SCHEDULED, AppointmentType.ONLINE, null);
    }
}
