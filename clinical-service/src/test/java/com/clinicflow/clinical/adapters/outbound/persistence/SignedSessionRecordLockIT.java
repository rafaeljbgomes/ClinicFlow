package com.clinicflow.clinical.adapters.outbound.persistence;

import com.clinicflow.clinical.application.exceptions.StaleResourceVersionException;
import com.clinicflow.clinical.domain.AppointmentId;
import com.clinicflow.clinical.domain.AttendanceStatus;
import com.clinicflow.clinical.domain.NoteStatus;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PsychologistId;
import com.clinicflow.clinical.domain.SessionModality;
import com.clinicflow.clinical.domain.SessionRecord;
import com.clinicflow.clinical.domain.SessionRecordId;
import com.clinicflow.clinical.domain.SignedSessionRecordException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

// Each step commits on its own so the PostgreSQL trigger and CHECK constraint see real row versions.
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaSessionRecordRepository.class, ClinicalPersistenceMapperImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class SignedSessionRecordLockIT {
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final PsychologistId OWNER = new PsychologistId(UUID.randomUUID());
    private static final PatientId PATIENT = new PatientId(UUID.randomUUID());

    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired private JpaSessionRecordRepository sessions;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void removeLockedRows() {
        // TRUNCATE does not fire row-level triggers, so it can clear signed rows between tests.
        jdbc.execute("TRUNCATE TABLE session_record_link_conflicts, session_records");
    }

    static Stream<Arguments> statementsAgainstSignedRows() {
        return Stream.of(
                Arguments.of("rewrite summary", "UPDATE session_records SET summary = 'Rewritten' WHERE id = ?"),
                Arguments.of("unsign", "UPDATE session_records SET note_status = 'DRAFT', signed_at = NULL, "
                        + "signed_by = NULL WHERE id = ?"),
                Arguments.of("touch updated_at", "UPDATE session_records SET updated_at = now() WHERE id = ?"),
                Arguments.of("delete", "DELETE FROM session_records WHERE id = ?"));
    }

    static Stream<Arguments> inconsistentSignatureColumns() {
        return Stream.of(
                Arguments.of("DRAFT", Timestamp.from(NOW), OWNER.value()),
                Arguments.of("DRAFT", Timestamp.from(NOW), null),
                Arguments.of("SIGNED", null, null),
                Arguments.of("SIGNED", Timestamp.from(NOW), null),
                Arguments.of("SIGNED", null, OWNER.value()));
    }

    @Test
    void persistsTheSignatureAndReadsItBack() {
        // Arrange
        SessionRecord draft = draft("Summary");
        sessions.save(draft);
        SessionRecord signed = sessions.findById(draft.id()).orElseThrow().sign(OWNER, NOW.plusSeconds(60));

        // Act
        sessions.save(signed);

        // Assert
        SessionRecord stored = sessions.findById(draft.id()).orElseThrow();
        assertThat(stored.noteStatus()).isEqualTo(NoteStatus.SIGNED);
        assertThat(stored.signedAt()).contains(NOW.plusSeconds(60));
        assertThat(stored.signedBy()).contains(OWNER);
        assertThat(stored.updatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("statementsAgainstSignedRows")
    void databaseRejectsDirectChangesToSignedRows(String change, String sql) {
        // Arrange
        SessionRecordId id = signedRecord("Signed summary");

        // Act
        Throwable thrown = catchThrowable(() -> jdbc.update(sql, id.value()));

        // Assert
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sqlState(thrown)).isEqualTo("23L01");
    }

    @Test
    void databaseAllowsNoOpUpdatesOfSignedRows() {
        // Arrange
        SessionRecordId id = signedRecord("Signed summary");
        Map<String, Object> before = row(id);

        // Act
        int updated = jdbc.update("UPDATE session_records SET summary = summary WHERE id = ?", id.value());

        // Assert
        assertThat(updated).isEqualTo(1);
        assertThat(row(id)).isEqualTo(before);
    }

    @Test
    void unsignedRowsStayEditable() {
        // Arrange
        SessionRecord draft = draft("Draft summary");
        sessions.save(draft);

        // Act
        int updated = jdbc.update("UPDATE session_records SET summary = 'Edited draft' WHERE id = ?",
                draft.id().value());

        // Assert
        assertThat(updated).isEqualTo(1);
    }

    @Test
    void unsignedRowsStayDeletable() {
        // Arrange
        SessionRecord draft = draft("Draft summary");
        sessions.save(draft);

        // Act
        int deleted = jdbc.update("DELETE FROM session_records WHERE id = ?", draft.id().value());

        // Assert
        assertThat(deleted).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0} with signed_at={1} and signed_by={2}")
    @MethodSource("inconsistentSignatureColumns")
    void checkConstraintKeepsSignatureColumnsConsistentWithNoteStatus(String noteStatus, Timestamp signedAt,
                                                                       UUID signedBy) {
        // Act
        Throwable thrown = catchThrowable(() -> jdbc.update("""
                INSERT INTO session_records (id, psychologist_id, patient_id, session_date, modality,
                    duration_minutes, attendance_status, note_status, summary, created_at, updated_at,
                    signed_at, signed_by)
                VALUES (?, ?, ?, ?, 'ONLINE', 50, 'ATTENDED', ?, 'Summary', ?, ?, ?, ?)
                """, UUID.randomUUID(), OWNER.value(), PATIENT.value(), Timestamp.from(NOW), noteStatus,
                Timestamp.from(NOW), Timestamp.from(NOW), signedAt, signedBy));

        // Assert
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sqlState(thrown)).isEqualTo("23514");
    }

    // The version check rejects the stale write before the trigger sees it (ADR-024).
    @Test
    void staleDraftSaveAfterACommittedSignatureIsRejectedAsStale() {
        // Arrange
        SessionRecord stale = staleDraftOfASignedRecord("Original summary");

        // Act
        Throwable thrown = catchThrowable(() -> sessions.save(stale));

        // Assert
        assertThat(thrown).isInstanceOf(StaleResourceVersionException.class);
    }

    // A write that bypasses the domain but names the current version still meets the ADR-019 trigger.
    @Test
    void changedSignedRowAtItsCurrentVersionIsRejectedByTheDatabase() {
        // Arrange
        SessionRecord signed = sessions.findById(signedRecord("Signed summary")).orElseThrow();
        SessionRecord rewritten = SessionRecord.rehydrate(signed.id(), null, OWNER, PATIENT, null, NOW,
                SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED, NoteStatus.SIGNED, "Rewritten", "Focus", null,
                null, null, NOW, NOW.plusSeconds(60), signed.signedAt().orElseThrow(), OWNER,
                signed.version().orElseThrow());

        // Act
        Throwable thrown = catchThrowable(() -> sessions.save(rewritten));

        // Assert
        assertThat(thrown).isInstanceOf(SignedSessionRecordException.class);
    }

    @Test
    void rejectedStaleDraftSaveLeavesTheRowSigned() {
        // Arrange
        SessionRecord stale = staleDraftOfASignedRecord("Original summary");

        // Act
        catchThrowable(() -> sessions.save(stale));

        // Assert
        Map<String, Object> stored = row(stale.id());
        assertThat(stored).containsEntry("note_status", "SIGNED").containsEntry("summary", "Original summary");
        assertThat(stored.get("signed_at")).isNotNull();
    }

    @Test
    void otherIntegrityFailuresAreNotReportedAsSignedNotes() {
        // Arrange
        AppointmentId appointmentId = new AppointmentId(UUID.randomUUID());
        sessions.save(completedAppointmentSession(appointmentId));

        // Act
        Throwable thrown = catchThrowable(() -> sessions.save(completedAppointmentSession(appointmentId)));

        // Assert
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
    }

    private SessionRecord staleDraftOfASignedRecord(String summary) {
        SessionRecord draft = draft(summary);
        sessions.save(draft);
        SessionRecord stale = sessions.findById(draft.id()).orElseThrow();
        sessions.save(sessions.findById(draft.id()).orElseThrow().sign(OWNER, NOW.plusSeconds(60)));
        stale.update(null, NOW, SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED, NoteStatus.DRAFT,
                "Stale rewrite", null, null, null, null, NOW.plusSeconds(120));
        return stale;
    }

    private SessionRecord completedAppointmentSession(AppointmentId appointmentId) {
        return SessionRecord.fromCompletedAppointment(new SessionRecordId(UUID.randomUUID()), null, OWNER,
                PATIENT, appointmentId, NOW, SessionModality.ONLINE, 50, NOW);
    }

    private SessionRecordId signedRecord(String summary) {
        SessionRecord draft = draft(summary);
        sessions.save(draft);
        sessions.save(sessions.findById(draft.id()).orElseThrow().sign(OWNER, NOW.plusSeconds(60)));
        return draft.id();
    }

    private SessionRecord draft(String summary) {
        return SessionRecord.rehydrate(new SessionRecordId(UUID.randomUUID()), null, OWNER, PATIENT, null, NOW,
                SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED, NoteStatus.DRAFT, summary, "Focus", null,
                null, null, NOW, NOW, null, null, null);
    }

    private Map<String, Object> row(SessionRecordId id) {
        return jdbc.queryForMap("SELECT * FROM session_records WHERE id = ?", id.value());
    }

    private static String sqlState(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
