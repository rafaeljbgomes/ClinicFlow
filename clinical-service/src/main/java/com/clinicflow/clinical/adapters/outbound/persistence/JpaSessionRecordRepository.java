package com.clinicflow.clinical.adapters.outbound.persistence;

import com.clinicflow.clinical.application.ports.SessionRecordRepository;
import com.clinicflow.clinical.domain.AppointmentId;
import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PsychologistId;
import com.clinicflow.clinical.domain.SessionRecord;
import com.clinicflow.clinical.domain.SessionRecordId;
import com.clinicflow.clinical.domain.SignedSessionRecordException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaSessionRecordRepository implements SessionRecordRepository {
    // Raised by the session_records trigger in V2__lock_signed_session_records.sql.
    static final String SIGNED_RECORD_LOCKED_SQL_STATE = "23L01";

    private final SpringDataSessionRecordJpaRepository delegate;
    private final ClinicalPersistenceMapper mapper;

    public JpaSessionRecordRepository(SpringDataSessionRecordJpaRepository delegate, ClinicalPersistenceMapper mapper) {
        this.delegate = delegate;
        this.mapper = mapper;
    }

    @Override
    public Optional<SessionRecord> findById(SessionRecordId id) {
        return delegate.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<SessionRecord> findByAppointmentId(AppointmentId appointmentId) {
        return delegate.findByAppointmentId(appointmentId.value()).map(mapper::toDomain);
    }

    @Override
    public List<SessionRecord> findByClinicalCaseId(ClinicalCaseId clinicalCaseId) {
        return delegate.findByClinicalCaseId(clinicalCaseId.value()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<SessionRecord> findByPsychologistIdAndPatientId(PsychologistId psychologistId, PatientId patientId) {
        return delegate.findByPsychologistIdAndPatientId(psychologistId.value(), patientId.value())
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public void save(SessionRecord sessionRecord) {
        try {
            // Flush here so the database lock on signed rows surfaces as a domain error, not a commit failure.
            delegate.saveAndFlush(mapper.toJpa(sessionRecord));
        } catch (DataAccessException ex) {
            if (isSignedRecordLock(ex)) {
                throw new SignedSessionRecordException();
            }
            throw ex;
        }
    }

    private static boolean isSignedRecordLock(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && SIGNED_RECORD_LOCKED_SQL_STATE.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
