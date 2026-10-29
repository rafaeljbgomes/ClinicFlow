package com.clinicflow.notification;

import com.clinicflow.notification.application.ports.NotificationRepository;
import com.clinicflow.notification.domain.EventId;
import com.clinicflow.notification.domain.EventType;
import com.clinicflow.notification.domain.Notification;
import com.clinicflow.notification.domain.NotificationId;
import com.clinicflow.notification.domain.NotificationMessage;
import com.clinicflow.notification.domain.NotificationSubject;
import com.clinicflow.notification.domain.PsychologistId;
import com.clinicflow.notification.domain.RecipientAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "clinicflow.demo.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@Testcontainers
class NotificationApiIT {
    private static final UUID PSYCHOLOGIST_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_PSYCHOLOGIST_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired private RestTestClient http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NotificationRepository repository;
    @MockitoBean private JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanDatabaseAndConfigureSecurity() {
        jdbc.execute("TRUNCATE TABLE notifications");
        reset(jwtDecoder);
        when(jwtDecoder.decode("psychologist-token")).thenReturn(jwt(PSYCHOLOGIST_ID, "PSYCHOLOGIST"));
        when(jwtDecoder.decode("admin-token")).thenReturn(jwt(UUID.randomUUID(), "ADMIN"));
        when(jwtDecoder.decode("patient-token")).thenReturn(jwt(UUID.randomUUID(), "PATIENT"));
    }

    @Test
    void psychologistListContainsOnlyOwnedNotifications() {
        // Arrange
        save(PSYCHOLOGIST_ID);
        save(OTHER_PSYCHOLOGIST_ID);

        // Act
        var response = exchange("/notifications", HttpMethod.GET,
                new HttpEntity<>(bearer("psychologist-token")), Map.class);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getResponseBody().get("content")).hasSize(1);
        assertThat(pageOf(response.getResponseBody())).containsEntry("totalElements", 1);
    }

    @Test
    void psychologistCanReadAnOwnedNotification() {
        // Arrange
        UUID ownedId = save(PSYCHOLOGIST_ID);

        // Act
        var response = exchange("/notifications/" + ownedId, HttpMethod.GET,
                new HttpEntity<>(bearer("psychologist-token")), Map.class);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getResponseBody()).containsEntry("id", ownedId.toString());
    }

    @Test
    void administratorListContainsEveryNotification() {
        // Arrange
        save(PSYCHOLOGIST_ID);
        save(OTHER_PSYCHOLOGIST_ID);

        // Act
        var response = exchange("/notifications", HttpMethod.GET,
                new HttpEntity<>(bearer("admin-token")), Map.class);

        // Assert
        assertThat((List<?>) response.getResponseBody().get("content")).hasSize(2);
    }

    @Test
    void listPagesNewestFirstAndReportsTotals() {
        // Arrange
        UUID older = save(PSYCHOLOGIST_ID, Instant.parse("2026-06-01T10:00:00Z"));
        UUID newer = save(PSYCHOLOGIST_ID, Instant.parse("2026-06-01T11:00:00Z"));

        // Act
        var first = exchange("/notifications?size=1", HttpMethod.GET,
                new HttpEntity<>(bearer("psychologist-token")), Map.class);
        var second = exchange("/notifications?size=1&page=1", HttpMethod.GET,
                new HttpEntity<>(bearer("psychologist-token")), Map.class);

        // Assert
        assertThat(ids(first.getResponseBody())).containsExactly(newer.toString());
        assertThat(ids(second.getResponseBody())).containsExactly(older.toString());
        assertThat(pageOf(first.getResponseBody()))
                .containsEntry("totalElements", 2).containsEntry("totalPages", 2);
    }

    @Test
    void listRequiresAuthentication() {
        // Act
        var response = exchange("/notifications", HttpMethod.GET, HttpEntity.EMPTY, Map.class);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listRejectsPatientRole() {
        // Arrange
        var request = new HttpEntity<>(bearer("patient-token"));

        // Act
        var response = exchange("/notifications", HttpMethod.GET, request, Map.class);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void psychologistCannotSeeAnotherOwnersNotification() {
        // Arrange
        UUID otherId = save(OTHER_PSYCHOLOGIST_ID);

        // Act
        var response = exchange("/notifications/" + otherId, HttpMethod.GET,
                new HttpEntity<>(bearer("psychologist-token")), Map.class);

        // Assert
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private UUID save(UUID psychologistId) {
        return save(psychologistId, Instant.now());
    }

    private static List<Object> ids(Map<?, ?> page) {
        return ((List<?>) page.get("content")).stream().<Object>map(item -> ((Map<?, ?>) item).get("id")).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> pageOf(Map<?, ?> body) {
        return (Map<String, Object>) body.get("page");
    }

    private UUID save(UUID psychologistId, Instant createdAt) {
        UUID id = UUID.randomUUID();
        repository.save(Notification.sent(new NotificationId(id), new EventId(UUID.randomUUID()),
                new EventType("PatientCreated"), new PsychologistId(psychologistId),
                new RecipientAddress("patient@example.com"),
                new NotificationSubject("Patient profile created"),
                new NotificationMessage("A patient profile was created"), createdAt));
        return id;
    }

    private <T> EntityExchangeResult<T> exchange(String path, HttpMethod method, HttpEntity<?> request, Class<T> type) {
        var spec = http.method(method).uri(path).headers(headers -> headers.addAll(request.getHeaders()));
        return (request.hasBody() ? spec.body(request.getBody()) : spec).exchange().returnResult(type);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private Jwt jwt(UUID userId, String role) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(role.toLowerCase() + "-token")
                .header("alg", "none")
                .issuedAt(now).expiresAt(now.plusSeconds(3600))
                .issuer("http://localhost:8180/realms/clinicflow")
                .audience(List.of("clinicflow-api"))
                .subject(userId.toString())
                .claim("roles", List.of(role))
                .build();
    }
}
