package com.clinicflow.clinical.infrastructure.bootstrap;

import com.clinicflow.clinical.application.ClinicalService;
import com.clinicflow.clinical.application.commands.SignSessionRecordCommand;
import com.clinicflow.clinical.domain.PsychologistId;
import com.clinicflow.clinical.domain.SessionRecordId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest(properties = {
        "clinicflow.demo.bootstrap.enabled=true",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@Testcontainers
class DemoDataBootstrapIT {
    private static final UUID SOFIA = UUID.fromString("00000000-0000-4000-8000-000000000011");
    private static final UUID SEEDED_SIGNED_NOTE = UUID.fromString("33000000-0000-4000-8000-000000000002");
    private static final UUID SEEDED_DRAFT_NOTE = UUID.fromString("33000000-0000-4000-8000-000000000005");

    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired private DemoDataBootstrap bootstrap;
    @Autowired private ClinicalService clinicalService;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void rerunsCleanlyAfterSignedNotesExist() {
        assertThat(note(SEEDED_SIGNED_NOTE)).containsEntry("note_status", "SIGNED").containsEntry("signed_by", SOFIA);
        clinicalService.signSessionRecord(new SignSessionRecordCommand(
                new PsychologistId(SOFIA), new SessionRecordId(SEEDED_DRAFT_NOTE)));
        Map<String, Object> seededSigned = note(SEEDED_SIGNED_NOTE);
        Map<String, Object> signedInDemo = note(SEEDED_DRAFT_NOTE);

        // Same runner the next application start executes against the existing database.
        assertThatCode(() -> bootstrap.run(null)).doesNotThrowAnyException();

        assertThat(note(SEEDED_SIGNED_NOTE)).isEqualTo(seededSigned);
        assertThat(note(SEEDED_DRAFT_NOTE)).isEqualTo(signedInDemo).containsEntry("note_status", "SIGNED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM session_records", Integer.class)).isEqualTo(5);
    }

    private Map<String, Object> note(UUID id) {
        return jdbc.queryForMap("SELECT * FROM session_records WHERE id = ?", id);
    }
}
