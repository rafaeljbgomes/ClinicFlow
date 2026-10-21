package com.clinicflow.clinical.adapters.outbound.persistence;

import com.clinicflow.clinical.application.exceptions.StaleResourceVersionException;
import com.clinicflow.clinical.application.ports.CarePlanRepository;
import com.clinicflow.clinical.domain.CarePlan;
import com.clinicflow.clinical.domain.ClinicalCaseId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaCarePlanRepository implements CarePlanRepository {
    private final SpringDataCarePlanJpaRepository delegate;
    private final ClinicalPersistenceMapper mapper;

    public JpaCarePlanRepository(SpringDataCarePlanJpaRepository delegate, ClinicalPersistenceMapper mapper) {
        this.delegate = delegate;
        this.mapper = mapper;
    }

    @Override
    public Optional<CarePlan> findByClinicalCaseId(ClinicalCaseId clinicalCaseId) {
        return delegate.findByClinicalCaseId(clinicalCaseId.value()).map(mapper::toDomain);
    }

    @Override
    public CarePlan save(CarePlan carePlan) {
        try {
            // Flush here so a concurrent write surfaces as a version conflict, not a commit failure.
            return mapper.toDomain(delegate.saveAndFlush(mapper.toJpa(carePlan)));
        } catch (OptimisticLockingFailureException ex) {
            throw new StaleResourceVersionException();
        } catch (DataIntegrityViolationException ex) {
            // A case has at most one plan, so a concurrent first save of its plan collides here.
            if (carePlan.version().isEmpty()) {
                throw new StaleResourceVersionException();
            }
            throw ex;
        }
    }
}
