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
        when(jwtDecoder.decode("psychologist-token")).thenReturn(jwt("PSYCHOLOGIST"));
        when(jwtDecoder.decode("patient-token")).thenReturn(jwt("PATIENT"));
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

    private org.springframework.http.ResponseEntity<Map> exchange(String path, HttpMethod method, Object body) {
        return http.exchange(path, method, new HttpEntity<>(body, bearer("psychologist-token")), Map.class);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private Jwt jwt(String role) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(role.toLowerCase() + "-token")
                .header("alg", "none")
                .issuedAt(now).expiresAt(now.plusSeconds(3600))
                .claim("userId", PSYCHOLOGIST_ID.toString())
                .claim("role", role)
                .build();
    }
}
