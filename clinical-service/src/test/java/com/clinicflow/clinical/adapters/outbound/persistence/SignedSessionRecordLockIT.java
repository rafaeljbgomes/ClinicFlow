package com.clinicflow.clinical.adapters.outbound.persistence;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired private JpaSessionRecordRepository sessions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @AfterEach
    void removeLockedRows() {
        // TRUNCATE does not fire row-level triggers, so it can clear signed rows between tests.
        jdbc.execute("TRUNCATE TABLE session_records");
    }

    @Test
    void persistsTheSignatureAndReadsItBack() {
        SessionRecord draft = draft("Summary");
        sessions.save(draft);

        sessions.save(sessions.findById(draft.id()).orElseThrow().sign(OWNER, NOW.plusSeconds(60)));

        SessionRecord stored = sessions.findById(draft.id()).orElseThrow();
        assertThat(stored.noteStatus()).isEqualTo(NoteStatus.SIGNED);
        assertThat(stored.signedAt()).contains(NOW.plusSeconds(60));
        assertThat(stored.signedBy()).contains(OWNER);
        assertThat(stored.updatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void databaseRejectsDirectUpdatesAndDeletesOfSignedRowsButAllowsNoOpUpdates() {
        SessionRecordId id = signedRecord("Signed summary");
        Map<String, Object> before = row(id);

        assertLocked(() -> jdbc.update("UPDATE session_records SET summary = 'Rewritten' WHERE id = ?", id.value()));
        assertLocked(() -> jdbc.update("UPDATE session_records SET note_status = 'DRAFT', signed_at = NULL, "
                + "signed_by = NULL WHERE id = ?", id.value()));
        assertLocked(() -> jdbc.update("UPDATE session_records SET updated_at = now() WHERE id = ?", id.value()));
        assertLocked(() -> jdbc.update("DELETE FROM session_records WHERE id = ?", id.value()));

        assertThat(jdbc.update("UPDATE session_records SET summary = summary WHERE id = ?", id.value())).isEqualTo(1);
        assertThat(row(id)).isEqualTo(before);
    }

    @Test
    void unsignedRowsStayEditableAndDeletable() {
        SessionRecord draft = draft("Draft summary");
        sessions.save(draft);

        assertThat(jdbc.update("UPDATE session_records SET summary = 'Edited draft' WHERE id = ?",
                draft.id().value())).isEqualTo(1);
        assertThat(jdbc.update("DELETE FROM session_records WHERE id = ?", draft.id().value())).isEqualTo(1);
    }

    @Test
    void checkConstraintKeepsSignatureColumnsConsistentWithNoteStatus() {
        assertCheckViolation("DRAFT", Timestamp.from(NOW), OWNER.value());
        assertCheckViolation("DRAFT", Timestamp.from(NOW), null);
        assertCheckViolation("SIGNED", null, null);
        assertCheckViolation("SIGNED", Timestamp.from(NOW), null);
        assertCheckViolation("SIGNED", null, OWNER.value());
    }

    @Test
    void staleDraftSaveAfterACommittedSignatureIsRejectedAndTheRowStaysSigned() {
        SessionRecord draft = draft("Original summary");
        sessions.save(draft);
        TransactionTemplate editTransaction = new TransactionTemplate(transactionManager);
        TransactionTemplate signTransaction = new TransactionTemplate(transactionManager);
        signTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> editTransaction.executeWithoutResult(status -> {
            SessionRecord stale = sessions.findById(draft.id()).orElseThrow();
            signTransaction.executeWithoutResult(inner ->
                    sessions.save(sessions.findById(draft.id()).orElseThrow().sign(OWNER, NOW.plusSeconds(60))));
            stale.update(null, NOW, SessionModality.ONLINE, 50, AttendanceStatus.ATTENDED, NoteStatus.DRAFT,
                    "Stale rewrite", null, null, null, null, NOW.plusSeconds(120));
            sessions.save(stale);
        })).isInstanceOf(SignedSessionRecordException.class);

        Map<String, Object> stored = row(draft.id());
        assertThat(stored).containsEntry("note_status", "SIGNED").containsEntry("summary", "Original summary");
        assertThat(stored.get("signed_at")).isNotNull();
    }

    @Test
    void otherIntegrityFailuresAreNotReportedAsSignedNotes() {
        AppointmentId appointmentId = new AppointmentId(UUID.randomUUID());
        sessions.save(SessionRecord.fromCompletedAppointment(new SessionRecordId(UUID.randomUUID()), null, OWNER,
                PATIENT, appointmentId, NOW, SessionModality.ONLINE, 50, NOW));

        assertThatThrownBy(() -> sessions.save(SessionRecord.fromCompletedAppointment(
                new SessionRecordId(UUID.randomUUID()), null, OWNER, PATIENT, appointmentId, NOW,
                SessionModality.ONLINE, 50, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
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
                null, null, NOW, NOW, null, null);
    }

    private Map<String, Object> row(SessionRecordId id) {
        return jdbc.queryForMap("SELECT * FROM session_records WHERE id = ?", id.value());
    }

    private void assertLocked(Runnable statement) {
        assertThatThrownBy(statement::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(failure -> assertThat(sqlState(failure)).isEqualTo("23L01"));
    }

    private void assertCheckViolation(String noteStatus, Timestamp signedAt, UUID signedBy) {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO session_records (id, psychologist_id, patient_id, session_date, modality,
                    duration_minutes, attendance_status, note_status, summary, created_at, updated_at,
                    signed_at, signed_by)
                VALUES (?, ?, ?, ?, 'ONLINE', 50, 'ATTENDED', ?, 'Summary', ?, ?, ?, ?)
                """, UUID.randomUUID(), OWNER.value(), PATIENT.value(), Timestamp.from(NOW), noteStatus,
                Timestamp.from(NOW), Timestamp.from(NOW), signedAt, signedBy))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(failure -> assertThat(sqlState(failure)).isEqualTo("23514"));
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
