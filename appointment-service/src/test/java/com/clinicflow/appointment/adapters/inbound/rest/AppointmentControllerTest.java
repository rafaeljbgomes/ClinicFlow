package com.clinicflow.appointment.adapters.inbound.rest;

import com.clinicflow.appointment.application.AppointmentService;
import com.clinicflow.appointment.application.exceptions.AppointmentAccessDeniedException;
import com.clinicflow.appointment.application.results.AppointmentResult;
import com.clinicflow.appointment.domain.AppointmentStatus;
import com.clinicflow.appointment.domain.AppointmentType;
import com.clinicflow.appointment.infrastructure.security.SecurityConfig;
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
    void rejectsMissingOrWrongRole() throws Exception {
        mvc.perform(get("/appointments"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/appointments").with(jwtFor("ROLE_PATIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void executesAllAppointmentEndpointsForPsychologist() throws Exception {
        when(appointmentService.schedule(any())).thenReturn(result());
        when(appointmentService.listForPsychologist(any())).thenReturn(List.of(result()));
        when(appointmentService.get(any())).thenReturn(result());
        when(appointmentService.reschedule(any())).thenReturn(result());
        when(appointmentService.cancel(any())).thenReturn(result());
        when(appointmentService.complete(any())).thenReturn(result());

        mvc.perform(post("/appointments")
                        .with(jwtFor("ROLE_PSYCHOLOGIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "patientId": "%s",
                                  "scheduledAt": "%s",
                                  "type": "ONLINE"
                                }
                                """.formatted(PATIENT_ID, APPOINTMENT_DATE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(APPOINTMENT_ID.toString()));

        mvc.perform(get("/appointments").with(jwtFor("ROLE_PSYCHOLOGIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(APPOINTMENT_ID.toString()));

        mvc.perform(get("/appointments/{id}", APPOINTMENT_ID).with(jwtFor("ROLE_PSYCHOLOGIST")))
                .andExpect(status().isOk());

        mvc.perform(patch("/appointments/{id}/reschedule", APPOINTMENT_ID)
                        .with(jwtFor("ROLE_PSYCHOLOGIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newDate\":\"2099-06-13T10:00:00Z\"}"))
                .andExpect(status().isOk());

        mvc.perform(patch("/appointments/{id}/cancel", APPOINTMENT_ID)
                        .with(jwtFor("ROLE_PSYCHOLOGIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Patient requested cancellation\"}"))
                .andExpect(status().isOk());

        mvc.perform(patch("/appointments/{id}/complete", APPOINTMENT_ID)
                        .with(jwtFor("ROLE_PSYCHOLOGIST")))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsPastAppointmentDate() throws Exception {
        mvc.perform(post("/appointments")
                        .with(jwtFor("ROLE_PSYCHOLOGIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "patientId": "%s",
                                  "scheduledAt": "2020-01-01T10:00:00Z",
                                  "type": "ONLINE"
                                }
                                """.formatted(PATIENT_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void mapsOwnershipFailureToForbiddenProblemDetail() throws Exception {
        when(appointmentService.get(any())).thenThrow(new AppointmentAccessDeniedException());

        mvc.perform(get("/appointments/{id}", APPOINTMENT_ID).with(jwtFor("ROLE_PSYCHOLOGIST")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access denied"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(String authority) {
        return jwt()
                .jwt(token -> token.claim("userId", PSYCHOLOGIST_ID.toString()))
                .authorities(new SimpleGrantedAuthority(authority));
    }

    private AppointmentResult result() {
        return new AppointmentResult(APPOINTMENT_ID, PSYCHOLOGIST_ID, PATIENT_ID, APPOINTMENT_DATE,
                AppointmentStatus.SCHEDULED, AppointmentType.ONLINE, null);
    }
}
