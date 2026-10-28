package com.clinicflow.appointment.adapters.inbound.rest;

import com.clinicflow.appointment.application.queries.ListAppointmentsQuery;
import com.clinicflow.appointment.application.queries.PageQuery;
import com.clinicflow.appointment.application.queries.ScheduleOrder;
import com.clinicflow.appointment.application.results.PageResult;
import com.clinicflow.appointment.domain.PsychologistId;
import com.clinicflow.appointment.application.AppointmentService;
import com.clinicflow.appointment.application.exceptions.AppointmentNotFoundException;
import com.clinicflow.appointment.application.exceptions.OwnershipCheckUnavailableException;
import com.clinicflow.appointment.application.exceptions.PatientNotFoundException;
import com.clinicflow.appointment.application.exceptions.StaleResourceVersionException;
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
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    void listReturnsTheFirstPageOfPsychologistAppointmentsByDefault() throws Exception {
        // Arrange
        var query = new ListAppointmentsQuery(new PsychologistId(PSYCHOLOGIST_ID), null, null, Set.of(),
                ScheduleOrder.ASC, new PageQuery(0, PageQuery.DEFAULT_SIZE));
        when(appointmentService.listForPsychologist(query))
                .thenReturn(new PageResult<>(List.of(result()), 0, PageQuery.DEFAULT_SIZE, 1));
        var request = get("/appointments").with(jwtFor("ROLE_PSYCHOLOGIST"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(APPOINTMENT_ID.toString()))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1));
    }

    @Test
    void listPassesTheWindowStatusesAndPage() throws Exception {
        // Arrange
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-08T00:00:00Z");
        var query = new ListAppointmentsQuery(new PsychologistId(PSYCHOLOGIST_ID), from, to,
                Set.of(AppointmentStatus.SCHEDULED, AppointmentStatus.RESCHEDULED), ScheduleOrder.DESC,
                new PageQuery(1, 10));
        when(appointmentService.listForPsychologist(query)).thenReturn(new PageResult<>(List.of(), 1, 10, 4));
        var request = get("/appointments").param("from", from.toString()).param("to", to.toString())
                .param("status", "SCHEDULED", "RESCHEDULED").param("order", "DESC").param("page", "1").param("size", "10")
                .with(jwtFor("ROLE_PSYCHOLOGIST"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(4));
    }

    @Test
    void listRejectsAWindowThatEndsBeforeItStarts() throws Exception {
        // Arrange
        var request = get("/appointments").param("from", "2026-06-08T00:00:00Z")
                .param("to", "2026-06-01T00:00:00Z").with(jwtFor("ROLE_PSYCHOLOGIST"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isBadRequest());
        verifyNoInteractions(appointmentService);
    }

    @Test
    void listRejectsMalformedWindowsAndOversizedPages() throws Exception {
        // Act
        ResultActions malformed = mvc.perform(get("/appointments").param("from", "tomorrow")
                .with(jwtFor("ROLE_PSYCHOLOGIST")));
        ResultActions oversized = mvc.perform(get("/appointments")
                .param("size", String.valueOf(PageQuery.MAX_SIZE + 1)).with(jwtFor("ROLE_PSYCHOLOGIST")));

        // Assert
        malformed.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_parameter"));
        oversized.andExpect(status().isBadRequest());
        verifyNoInteractions(appointmentService);
    }

    @Test
    void getReturnsRequestedAppointment() throws Exception {
        // Arrange
        when(appointmentService.get(any())).thenReturn(result());
        var request = get("/appointments/{id}", APPOINTMENT_ID).with(jwtFor("ROLE_PSYCHOLOGIST"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"5\""))
                .andExpect(jsonPath("$.version").value(5));
    }

    @Test
    void rescheduleReturnsUpdatedAppointment() throws Exception {
        // Arrange
        when(appointmentService.reschedule(any())).thenReturn(result());
        var request = patch("/appointments/{id}/reschedule", APPOINTMENT_ID)
                .with(jwtFor("ROLE_PSYCHOLOGIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"4\"")
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
                .header("If-Match", "\"4\"")
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
                .with(jwtFor("ROLE_PSYCHOLOGIST"))
                .header("If-Match", "\"4\"");

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"5\""));
    }

    @Test
    void completeWithoutIfMatchIsPreconditionRequired() throws Exception {
        // Arrange
        var request = patch("/appointments/{id}/complete", APPOINTMENT_ID).with(jwtFor("ROLE_PSYCHOLOGIST"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("precondition_required"));
    }

    @Test
    void completeWithoutIfMatchNeverReachesTheService() throws Exception {
        // Arrange
        var request = patch("/appointments/{id}/complete", APPOINTMENT_ID).with(jwtFor("ROLE_PSYCHOLOGIST"));

        // Act
        mvc.perform(request);

        // Assert
        verifyNoInteractions(appointmentService);
    }

    @Test
    void cancelWithAWildcardIfMatchIsRejected() throws Exception {
        // Arrange
        var request = patch("/appointments/{id}/cancel", APPOINTMENT_ID)
                .with(jwtFor("ROLE_PSYCHOLOGIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "*")
                .content("{\"reason\":\"Patient requested cancellation\"}");

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_precondition"));
    }

    @Test
    void rescheduleWithoutIfMatchIsPreconditionRequired() throws Exception {
        // Arrange
        var request = patch("/appointments/{id}/reschedule", APPOINTMENT_ID)
                .with(jwtFor("ROLE_PSYCHOLOGIST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newDate\":\"2099-06-13T10:00:00Z\"}");

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isPreconditionRequired());
    }

    @Test
    void mapsAStaleVersionToPreconditionFailed() throws Exception {
        // Arrange
        when(appointmentService.complete(any())).thenThrow(new StaleResourceVersionException());
        var request = patch("/appointments/{id}/complete", APPOINTMENT_ID)
                .with(jwtFor("ROLE_PSYCHOLOGIST"))
                .header("If-Match", "\"4\"");

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("stale_resource_version"));
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
    void mapsUnknownOrForeignAppointmentToNotFoundProblemDetail() throws Exception {
        // Arrange
        when(appointmentService.get(any())).thenThrow(new AppointmentNotFoundException());
        var request = get("/appointments/{id}", APPOINTMENT_ID).with(jwtFor("ROLE_PSYCHOLOGIST"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("appointment_not_found"));
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
                AppointmentStatus.SCHEDULED, AppointmentType.ONLINE, null, 5);
    }
}
