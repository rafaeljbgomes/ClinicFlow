package com.clinicflow.patient.adapters.outbound.persistence;

import com.clinicflow.patient.application.exceptions.StaleResourceVersionException;
import com.clinicflow.patient.application.ports.PatientRepository;
import com.clinicflow.patient.domain.Patient;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PsychologistId;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaPatientRepository implements PatientRepository {
    private final SpringDataPatientJpaRepository delegate;
    private final PatientPersistenceMapper mapper;

    public JpaPatientRepository(SpringDataPatientJpaRepository delegate, PatientPersistenceMapper mapper) {
        this.delegate = delegate;
        this.mapper = mapper;
    }

    @Override
    public Patient save(Patient patient) {
        try {
            // Flush here so a concurrent write surfaces as a version conflict, not a commit failure.
            return mapper.toDomain(delegate.saveAndFlush(mapper.toJpa(patient)));
        } catch (OptimisticLockingFailureException ex) {
            throw new StaleResourceVersionException();
        }
    }

    @Override
    public Optional<Patient> findById(PatientId id) {
        return delegate.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<Patient> findByPsychologistId(PsychologistId psychologistId) {
        return delegate.findByPsychologistId(psychologistId.value()).stream().map(mapper::toDomain).toList();
    }
}
