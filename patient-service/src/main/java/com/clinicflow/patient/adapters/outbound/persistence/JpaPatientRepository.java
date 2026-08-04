package com.clinicflow.patient.adapters.outbound.persistence;

import com.clinicflow.patient.application.ports.PatientRepository;
import com.clinicflow.patient.domain.Patient;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PsychologistId;
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
    public void save(Patient patient) {
        delegate.save(mapper.toJpa(patient));
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
