package com.clinicflow.clinical.adapters.outbound.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Testcontainers
class SessionRecordSignatureMigrationIT {
    private static final Instant SIGNED_UPDATE = Instant.parse("2026-05-20T09:30:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    private final UUID psychologistId = UUID.randomUUID();
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private String schema;

    @BeforeEach
    void migrateAnIsolatedSchemaToTheFirstRelease() {
        schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
        String url = POSTGRES.getJdbcUrl();
        dataSource = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        flyway("1").migrate();
    }

    @Test
    void backfillsSignatureForNotesSignedBeforeTheMigration() {
        // Arrange
        UUID signedId = legacySession("SIGNED");

        // Act
        migrateToLatest();

        // Assert
        assertThat(signature(signedId)).containsEntry("signed_at", Timestamp.from(SIGNED_UPDATE))
                .containsEntry("signed_by", psychologistId);
    }

    @Test
    void leavesDraftNotesUnsigned() {
        // Arrange
        UUID draftId = legacySession("DRAFT");

        // Act
        migrateToLatest();

        // Assert
        assertThat(signature(draftId)).containsEntry("signed_at", null).containsEntry("signed_by", null);
    }

    @Test
    void keepsSignatureConsistentWithNoteStatus() {
        // Arrange
        legacySession("SIGNED");
        legacySession("DRAFT");

        // Act
        migrateToLatest();

        // Assert
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM session_records
                WHERE (note_status = 'SIGNED') <> (signed_at IS NOT NULL AND signed_by IS NOT NULL)
                """, Integer.class)).isZero();
    }

    @Test
    void rejectsClearingTheSignatureTimestampOfAMigratedSignedNote() {
        // Arrange
        UUID signedId = legacySession("SIGNED");
        migrateToLatest();

        // Act
        Throwable thrown = catchThrowable(() ->
                jdbc.update("UPDATE session_records SET signed_at = NULL WHERE id = ?", signedId));

        // Assert
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsChangingTheSignerOfAMigratedSignedNote() {
        // Arrange
        UUID signedId = legacySession("SIGNED");
        migrateToLatest();

        // Act
        Throwable thrown = catchThrowable(() -> jdbc.update(
                "UPDATE session_records SET signed_by = ? WHERE id = ?", UUID.randomUUID(), signedId));

        // Assert
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void keepsMigratedDraftNotesEditable() {
        // Arrange
        UUID draftId = legacySession("DRAFT");
        migrateToLatest();

        // Act
        int updated = jdbc.update("UPDATE session_records SET summary = 'Edited draft' WHERE id = ?", draftId);

        // Assert
        assertThat(updated).isEqualTo(1);
    }

    private UUID legacySession(String noteStatus) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO session_records (id, psychologist_id, patient_id, session_date, modality,
                    duration_minutes, attendance_status, note_status, summary, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ONLINE', 50, 'ATTENDED', ?, 'Legacy note', ?, ?)
                """, id, psychologistId, UUID.randomUUID(), Timestamp.from(SIGNED_UPDATE.minusSeconds(3600)),
                noteStatus, Timestamp.from(SIGNED_UPDATE.minusSeconds(3600)), Timestamp.from(SIGNED_UPDATE));
        return id;
    }

    private void migrateToLatest() {
        flyway("latest").migrate();
    }

    private Map<String, Object> signature(UUID id) {
        return jdbc.queryForMap("SELECT signed_at, signed_by FROM session_records WHERE id = ?", id);
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }
}
