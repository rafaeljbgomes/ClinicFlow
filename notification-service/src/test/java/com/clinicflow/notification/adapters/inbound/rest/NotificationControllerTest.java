package com.clinicflow.notification.adapters.inbound.rest;

import com.clinicflow.notification.application.NotificationService;
import com.clinicflow.notification.application.exceptions.NotificationNotFoundException;
import com.clinicflow.notification.application.queries.ListNotificationsQuery;
import com.clinicflow.notification.application.queries.PageQuery;
import com.clinicflow.notification.application.results.NotificationResult;
import com.clinicflow.notification.application.results.PageResult;
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
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
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
    void listReturnsTheFirstPageForPsychologistByDefault() throws Exception {
        // Arrange
        var query = new ListNotificationsQuery(Set.of(), new PageQuery(0, PageQuery.DEFAULT_SIZE));
        when(notificationService.listForPsychologist(eq(query), any()))
                .thenReturn(new PageResult<>(List.of(result()), 0, PageQuery.DEFAULT_SIZE, 41));
        var request = get("/notifications").with(jwtFor("ROLE_PSYCHOLOGIST"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(NOTIFICATION_ID.toString()))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(PageQuery.DEFAULT_SIZE))
                .andExpect(jsonPath("$.page.totalElements").value(41))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    @Test
    void listPassesPageSizeAndStatusFilterForAdministrator() throws Exception {
        // Arrange
        var query = new ListNotificationsQuery(Set.of(NotificationStatus.PENDING, NotificationStatus.FAILED),
                new PageQuery(2, 50));
        when(notificationService.list(query)).thenReturn(new PageResult<>(List.of(), 2, 50, 0));
        var request = get("/notifications").param("page", "2").param("size", "50")
                .param("status", "PENDING", "FAILED").with(jwtFor("ROLE_ADMIN"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page.number").value(2));
    }

    @Test
    void listRejectsPageSizesAboveTheLimit() throws Exception {
        // Arrange
        var request = get("/notifications").param("size", String.valueOf(PageQuery.MAX_SIZE + 1))
                .with(jwtFor("ROLE_PSYCHOLOGIST"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
        verifyNoInteractions(notificationService);
    }

    @Test
    void listRejectsUnknownStatusFilters() throws Exception {
        // Arrange
        var request = get("/notifications").param("status", "DELIVERED").with(jwtFor("ROLE_PSYCHOLOGIST"));

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_parameter"));
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
        return jwt().jwt(token -> token.subject(PSYCHOLOGIST_ID.toString()))
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
