package com.clinicflow.notification.adapters.inbound.rest;

import com.clinicflow.notification.application.NotificationService;
import com.clinicflow.notification.application.exceptions.NotificationNotFoundException;
import com.clinicflow.notification.application.results.NotificationResult;
import com.clinicflow.notification.domain.NotificationStatus;
import com.clinicflow.notification.domain.NotificationType;
import com.clinicflow.notification.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, NotificationRestMapperImpl.class})
class NotificationControllerTest {
    private static final UUID NOTIFICATION_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID EVENT_ID =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PSYCHOLOGIST_ID =
            UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        // Arrange
        var request = get("/notifications");

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsPatientRole() throws Exception {
        // Arrange
        var request = get("/notifications").with(jwtFor("ROLE_PATIENT"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isForbidden());
    }

    @Test
    void listReturnsNotificationsForPsychologist() throws Exception {
        // Arrange
        when(notificationService.listForPsychologist(any())).thenReturn(List.of(result()));
        var request = get("/notifications").with(jwtFor("ROLE_PSYCHOLOGIST"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(NOTIFICATION_ID.toString()));
    }

    @Test
    void getReturnsNotificationForAdministrator() throws Exception {
        // Arrange
        when(notificationService.get(any())).thenReturn(result());
        var request = get("/notifications/{id}", NOTIFICATION_ID).with(jwtFor("ROLE_ADMIN"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(EVENT_ID.toString()));
    }

    @Test
    void mapsMissingNotificationToNotFoundProblemDetail() throws Exception {
        // Arrange
        when(notificationService.get(any())).thenThrow(new NotificationNotFoundException());
        var request = get("/notifications/{id}", NOTIFICATION_ID).with(jwtFor("ROLE_ADMIN"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(String authority) {
        String role = authority.replace("ROLE_", "");
        return jwt().jwt(token -> token.claim("role", role).claim("userId", PSYCHOLOGIST_ID.toString()))
                .authorities(new SimpleGrantedAuthority(authority));
    }

    private NotificationResult result() {
        Instant now = Instant.parse("2026-06-01T10:00:00Z");
        return new NotificationResult(NOTIFICATION_ID, EVENT_ID, "PatientCreated",
                PSYCHOLOGIST_ID,
                NotificationType.EMAIL, NotificationStatus.SENT, "patient@example.com",
                "Patient profile created", now, now);
    }
}
