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
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

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
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired private DemoDataBootstrap bootstrap;
    @Autowired private ClinicalService clinicalService;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void startupSeedsSignedNoteWithItsSignature() {
        // Act
        Map<String, Object> seeded = note(SEEDED_SIGNED_NOTE);

        // Assert
        assertThat(seeded).containsEntry("note_status", "SIGNED").containsEntry("signed_by", SOFIA);
    }

    @Test
    void rerunsCleanlyAfterSignedNotesExist() {
        // Arrange
        ensureSeededDraftIsSigned();

        // Act
        // Same runner the next application start executes against the existing database.
        Throwable thrown = catchThrowable(() -> bootstrap.run(null));

        // Assert
        assertThat(thrown).isNull();
    }

    @Test
    void rerunLeavesSeededAndDemoSignedNotesUntouched() {
        // Arrange
        ensureSeededDraftIsSigned();
        Map<String, Object> seededSigned = note(SEEDED_SIGNED_NOTE);
        Map<String, Object> signedInDemo = note(SEEDED_DRAFT_NOTE);

        // Act
        bootstrap.run(null);

        // Assert
        assertThat(note(SEEDED_SIGNED_NOTE)).isEqualTo(seededSigned);
        assertThat(note(SEEDED_DRAFT_NOTE)).isEqualTo(signedInDemo).containsEntry("note_status", "SIGNED");
    }

    @Test
    void rerunDoesNotDuplicateSessionRecords() {
        // Arrange
        ensureSeededDraftIsSigned();

        // Act
        bootstrap.run(null);

        // Assert
        assertThat(jdbc.queryForObject("SELECT count(*) FROM session_records", Integer.class)).isEqualTo(5);
    }

    private void ensureSeededDraftIsSigned() {
        if (!"SIGNED".equals(note(SEEDED_DRAFT_NOTE).get("note_status"))) {
            clinicalService.signSessionRecord(new SignSessionRecordCommand(
                    new PsychologistId(SOFIA), new SessionRecordId(SEEDED_DRAFT_NOTE),
                    currentVersion(SEEDED_DRAFT_NOTE)));
        }
    }

    private long currentVersion(UUID id) {
        return ((Number) note(id).get("version")).longValue();
    }

    private Map<String, Object> note(UUID id) {
        return jdbc.queryForMap("SELECT * FROM session_records WHERE id = ?", id);
    }
}
