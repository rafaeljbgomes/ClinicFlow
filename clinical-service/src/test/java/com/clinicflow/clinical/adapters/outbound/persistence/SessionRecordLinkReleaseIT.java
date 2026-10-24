package com.clinicflow.clinical.adapters.outbound.persistence;

import com.clinicflow.clinical.domain.AppointmentId;
import com.clinicflow.clinical.domain.AttendanceStatus;
import com.clinicflow.clinical.domain.LinkConflictSource;
import com.clinicflow.clinical.domain.NoteStatus;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PsychologistId;
import com.clinicflow.clinical.domain.SessionModality;
import com.clinicflow.clinical.domain.SessionRecord;
import com.clinicflow.clinical.domain.SessionRecordId;
import com.clinicflow.clinical.domain.SessionRecordLinkConflict;
import com.clinicflow.clinical.domain.SessionRecordLinkConflictId;
import com.clinicflow.clinical.domain.SignedSessionRecordException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

// ADR-025: a signed note may lose an appointment link it never owned, but only once the conflict is recorded.
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaSessionRecordRepository.class, JpaSessionRecordLinkConflictRepository.class,
        ClinicalPersistenceMapperImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class SessionRecordLinkReleaseIT {
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final PsychologistId CLAIMANT = new PsychologistId(UUID.randomUUID());
    private static final PatientId CLAIMANT_PATIENT = new PatientId(UUID.randomUUID());
    private static final PsychologistId OWNER = new PsychologistId(UUID.randomUUID());
    private static final PatientId OWNER_PATIENT = new PatientId(UUID.randomUUID());

    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired private JpaSessionRecordRepository sessions;
    @Autowired private JpaSessionRecordLinkConflictRepository conflicts;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void removeRows() {
        // TRUNCATE does not fire row-level triggers, so it can clear signed rows between tests.
        jdbc.execute("TRUNCATE TABLE session_record_link_conflicts, session_records");
    }

    // Each statement bumps the version like persistence does, so only the named change can trip the trigger.
    static Stream<Arguments> changesBesideTheRecordedRelease() {
        return Stream.of(
                Arguments.of("rewrite summary", "UPDATE session_records SET appointment_id = NULL, "
                        + "version = version + 1, summary = 'Rewritten' WHERE id = ?"),
                Arguments.of("touch updated_at", "UPDATE session_records SET appointment_id = NULL, "
                        + "version = version + 1, updated_at = now() WHERE id = ?"),
                Arguments.of("unsign", "UPDATE session_records SET appointment_id = NULL, version = version + 1, "
                        + "note_status = 'DRAFT', signed_at = NULL, signed_by = NULL WHERE id = ?"),
                Arguments.of("relink to another appointment", "UPDATE session_records SET appointment_id = "
                        + "gen_random_uuid(), version = version + 1 WHERE id = ?"),
                Arguments.of("keep the version", "UPDATE session_records SET appointment_id = NULL WHERE id = ?"),
                Arguments.of("skip a version", "UPDATE session_records SET appointment_id = NULL, "
                        + "version = version + 2 WHERE id = ?"),
                Arguments.of("delete", "DELETE FROM session_records WHERE id = ?"));
    }

    @Test
    void signedRecordReleasesARecordedConflictingLink() {
        // Arrange
        SessionRecord claim = signedClaim();
        SessionRecordLinkConflict conflict = release(claim);

        // Act
        conflicts.save(conflict);
        sessions.save(claim);

        // Assert
        assertThat(sessions.findById(claim.id()).orElseThrow().appointmentId()).isEmpty();
    }

    @Test
    void releasedSignedRecordKeepsItsNoteAndSignature() {
        // Arrange
        SessionRecord claim = signedClaim();
        Map<String, Object> before = row(claim.id());
        conflicts.save(release(claim));

        // Act
        sessions.save(claim);

        // Assert
        Map<String, Object> after = row(claim.id());
        for (String released : new String[] {"appointment_id", "version"}) {
            before.remove(released);
            after.remove(released);
        }
        assertThat(after).isEqualTo(before);
    }

    @Test
    void releaseMovesTheSignedRecordToItsNextVersion() {
        // Arrange
        SessionRecord claim = signedClaim();
        long signedVersion = claim.version().orElseThrow();
        conflicts.save(release(claim));

        // Act
        SessionRecord released = sessions.save(claim);

        // Assert
        assertThat(released.version()).contains(signedVersion + 1);
    }

    @Test
    void storedConflictKeepsTheReleasedLinkAndTheAppointmentOwner() {
        // Arrange
        SessionRecord claim = signedClaim();
        AppointmentId appointmentId = claim.appointmentId().orElseThrow();
        SessionRecordLinkConflict conflict = release(claim);

        // Act
        conflicts.save(conflict);

        // Assert
        assertThat(jdbc.queryForMap("SELECT * FROM session_record_link_conflicts WHERE id = ?",
                conflict.id().value()))
                .containsEntry("session_record_id", claim.id().value())
                .containsEntry("appointment_id", appointmentId.value())
                .containsEntry("appointment_psychologist_id", OWNER.value())
                .containsEntry("appointment_patient_id", OWNER_PATIENT.value())
                .containsEntry("source", "APPOINTMENT_COMPLETED")
                .containsEntry("detected_at", Timestamp.from(NOW.plusSeconds(120)));
    }

    @Test
    void ownersRecordTakesTheReleasedAppointment() {
        // Arrange
        SessionRecord claim = signedClaim();
        AppointmentId appointmentId = claim.appointmentId().orElseThrow();
        conflicts.save(release(claim));
        sessions.save(claim);

        // Act
        sessions.save(SessionRecord.fromCompletedAppointment(new SessionRecordId(UUID.randomUUID()), null, OWNER,
                OWNER_PATIENT, appointmentId, NOW, SessionModality.ONLINE, 50, NOW));

        // Assert
        assertThat(sessions.findByAppointmentId(appointmentId)).hasValueSatisfying(record ->
                assertThat(record.psychologistId()).isEqualTo(OWNER));
    }

    @Test
    void signedRecordCannotDropAnUnrecordedLink() {
        // Arrange
        SessionRecord claim = signedClaim();
        release(claim);

        // Act
        Throwable thrown = catchThrowable(() -> sessions.save(claim));

        // Assert
        assertThat(thrown).isInstanceOf(SignedSessionRecordException.class);
    }

    @Test
    void conflictRecordedForAnotherAppointmentDoesNotUnlockTheRecord() {
        // Arrange
        SessionRecord claim = signedClaim();
        conflicts.save(new SessionRecordLinkConflict(new SessionRecordLinkConflictId(UUID.randomUUID()),
                claim.id(), new AppointmentId(UUID.randomUUID()), OWNER, OWNER_PATIENT,
                LinkConflictSource.MANUAL_LINK, NOW));

        // Act
        Throwable thrown = catchThrowable(() -> jdbc.update(
                "UPDATE session_records SET appointment_id = NULL, version = version + 1 WHERE id = ?",
                claim.id().value()));

        // Assert
        assertThat(sqlState(thrown)).isEqualTo("23L01");
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("changesBesideTheRecordedRelease")
    void recordedConflictUnlocksNothingButTheRelease(String change, String sql) {
        // Arrange
        SessionRecord claim = signedClaim();
        conflicts.save(release(claim));

        // Act
        Throwable thrown = catchThrowable(() -> jdbc.update(sql, claim.id().value()));

        // Assert
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sqlState(thrown)).isEqualTo("23L01");
    }

    @Test
    void conflictMustReferenceAStoredSessionRecord() {
        // Arrange
        SessionRecordLinkConflict orphan = new SessionRecordLinkConflict(
                new SessionRecordLinkConflictId(UUID.randomUUID()), new SessionRecordId(UUID.randomUUID()),
                new AppointmentId(UUID.randomUUID()), OWNER, OWNER_PATIENT, LinkConflictSource.MANUAL_LINK, NOW);

        // Act
        Throwable thrown = catchThrowable(() -> conflicts.save(orphan));

        // Assert
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
    }

    private SessionRecord signedClaim() {
        SessionRecord draft = SessionRecord.rehydrate(new SessionRecordId(UUID.randomUUID()), null, CLAIMANT,
                CLAIMANT_PATIENT, new AppointmentId(UUID.randomUUID()), NOW, SessionModality.ONLINE, 50,
                AttendanceStatus.ATTENDED, NoteStatus.DRAFT, "Signed summary", "Focus", null, null, null, NOW, NOW,
                null, null, null);
        sessions.save(draft);
        sessions.save(sessions.findById(draft.id()).orElseThrow().sign(CLAIMANT, NOW.plusSeconds(60)));
        return sessions.findById(draft.id()).orElseThrow();
    }

    private static SessionRecordLinkConflict release(SessionRecord claim) {
        return claim.releaseAppointmentLink(new SessionRecordLinkConflictId(UUID.randomUUID()), OWNER,
                OWNER_PATIENT, LinkConflictSource.APPOINTMENT_COMPLETED, NOW.plusSeconds(120));
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
