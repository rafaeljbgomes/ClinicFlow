package com.clinicflow.clinical.adapters.outbound.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class SessionRecordSignatureMigrationIT {
    private static final Instant SIGNED_UPDATE = Instant.parse("2026-05-20T09:30:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    @Test
    void backfillsNotesSignedBeforeTheMigrationAndThenLocksThem() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        flyway(dataSource, "1").migrate();
        UUID psychologistId = UUID.randomUUID();
        UUID signedId = insertV1Session(jdbc, psychologistId, "SIGNED");
        UUID draftId = insertV1Session(jdbc, psychologistId, "DRAFT");

        flyway(dataSource, "latest").migrate();

        Map<String, Object> signed = jdbc.queryForMap(
                "SELECT signed_at, signed_by FROM session_records WHERE id = ?", signedId);
        assertThat(signed).containsEntry("signed_at", Timestamp.from(SIGNED_UPDATE))
                .containsEntry("signed_by", psychologistId);
        assertThat(jdbc.queryForMap("SELECT signed_at, signed_by FROM session_records WHERE id = ?", draftId))
                .containsEntry("signed_at", null).containsEntry("signed_by", null);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM session_records
                WHERE (note_status = 'SIGNED') <> (signed_at IS NOT NULL AND signed_by IS NOT NULL)
                """, Integer.class)).isZero();

        assertThatThrownBy(() -> jdbc.update("UPDATE session_records SET signed_at = NULL WHERE id = ?", signedId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE session_records SET signed_by = ? WHERE id = ?",
                UUID.randomUUID(), signedId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update("UPDATE session_records SET summary = 'Edited draft' WHERE id = ?", draftId))
                .isEqualTo(1);
    }

    private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private static UUID insertV1Session(JdbcTemplate jdbc, UUID psychologistId, String noteStatus) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO session_records (id, psychologist_id, patient_id, session_date, modality,
                    duration_minutes, attendance_status, note_status, summary, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ONLINE', 50, 'ATTENDED', ?, 'Legacy note', ?, ?)
                """, id, psychologistId, UUID.randomUUID(), Timestamp.from(SIGNED_UPDATE.minusSeconds(3600)),
                noteStatus, Timestamp.from(SIGNED_UPDATE.minusSeconds(3600)), Timestamp.from(SIGNED_UPDATE));
        return id;
    }
}
