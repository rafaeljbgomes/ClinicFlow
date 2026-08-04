package com.clinicflow.clinical.adapters.outbound.persistence;

import com.clinicflow.clinical.application.ports.SessionRecordRepository;
import com.clinicflow.clinical.domain.AppointmentId;
import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PsychologistId;
import com.clinicflow.clinical.domain.SessionRecord;
import com.clinicflow.clinical.domain.SessionRecordId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaSessionRecordRepository implements SessionRecordRepository {
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
        delegate.save(mapper.toJpa(sessionRecord));
    }
}
