package com.clinicflow.clinical.adapters.outbound.persistence;

import com.clinicflow.clinical.application.exceptions.StaleResourceVersionException;
import com.clinicflow.clinical.application.ports.ClinicalCaseRepository;
import com.clinicflow.clinical.domain.ClinicalCase;
import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.ClinicalCaseStatus;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PsychologistId;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaClinicalCaseRepository implements ClinicalCaseRepository {
    private final SpringDataClinicalCaseJpaRepository delegate;
    private final ClinicalPersistenceMapper mapper;

    public JpaClinicalCaseRepository(SpringDataClinicalCaseJpaRepository delegate,
                                     ClinicalPersistenceMapper mapper) {
        this.delegate = delegate;
        this.mapper = mapper;
    }

    @Override
    public Optional<ClinicalCase> findById(ClinicalCaseId id) {
        return delegate.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<ClinicalCase> findByPsychologistId(PsychologistId psychologistId) {
        return delegate.findByPsychologistId(psychologistId.value()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<ClinicalCase> findByPsychologistIdAndPatientId(PsychologistId psychologistId, PatientId patientId) {
        return delegate.findByPsychologistIdAndPatientId(psychologistId.value(), patientId.value())
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<ClinicalCase> findByPsychologistIdAndStatus(PsychologistId psychologistId,
                                                            ClinicalCaseStatus status) {
        return delegate.findByPsychologistIdAndStatus(psychologistId.value(), status)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<ClinicalCase> findOpenByPsychologistIdAndPatientId(PsychologistId psychologistId,
                                                                       PatientId patientId) {
        return delegate.findFirstByPsychologistIdAndPatientIdAndStatusNot(
                psychologistId.value(), patientId.value(), ClinicalCaseStatus.DISCHARGED).map(mapper::toDomain);
    }

    @Override
    public boolean existsOpenByPsychologistIdAndPatientId(PsychologistId psychologistId, PatientId patientId) {
        return delegate.existsByPsychologistIdAndPatientIdAndStatusNot(
                psychologistId.value(), patientId.value(), ClinicalCaseStatus.DISCHARGED);
    }

    @Override
    public ClinicalCase save(ClinicalCase clinicalCase) {
        try {
            // Flush here so a concurrent write surfaces as a version conflict, not a commit failure.
            return mapper.toDomain(delegate.saveAndFlush(mapper.toJpa(clinicalCase)));
        } catch (OptimisticLockingFailureException ex) {
            throw new StaleResourceVersionException();
        }
    }
}
