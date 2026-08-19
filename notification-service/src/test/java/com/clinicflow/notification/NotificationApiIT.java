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
class NotificationApiIT {
    private static final UUID PSYCHOLOGIST_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_PSYCHOLOGIST_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired private TestRestTemplate http;
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
    void filtersNotificationsForPsychologistsWhileAdminsSeeAll() {
        UUID ownedId = save(PSYCHOLOGIST_ID);
        save(OTHER_PSYCHOLOGIST_ID);

        var psychologistList = http.exchange("/notifications", HttpMethod.GET,
                new HttpEntity<>(bearer("psychologist-token")), Object[].class);
        assertThat(psychologistList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(psychologistList.getBody()).hasSize(1);

        var detail = http.exchange("/notifications/" + ownedId, HttpMethod.GET,
                new HttpEntity<>(bearer("psychologist-token")), Map.class);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody()).containsEntry("id", ownedId.toString());

        var adminList = http.exchange("/notifications", HttpMethod.GET,
                new HttpEntity<>(bearer("admin-token")), Object[].class);
        assertThat(adminList.getBody()).hasSize(2);
    }

    @Test
    void rejectsUnauthenticatedAndUnsupportedRolesAndHidesOtherOwnersRecords() {
        UUID otherId = save(OTHER_PSYCHOLOGIST_ID);
        assertThat(http.getForEntity("/notifications", Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.exchange("/notifications", HttpMethod.GET,
                new HttpEntity<>(bearer("patient-token")), Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(http.exchange("/notifications/" + otherId, HttpMethod.GET,
                new HttpEntity<>(bearer("psychologist-token")), Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private UUID save(UUID psychologistId) {
        UUID id = UUID.randomUUID();
        repository.save(Notification.sent(new NotificationId(id), new EventId(UUID.randomUUID()),
                new EventType("PatientCreated"), new PsychologistId(psychologistId),
                new RecipientAddress("patient@example.com"),
                new NotificationSubject("Patient profile created"),
                new NotificationMessage("A patient profile was created"), Instant.now()));
        return id;
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
                .claim("userId", userId.toString())
                .claim("role", role)
                .build();
    }
}
