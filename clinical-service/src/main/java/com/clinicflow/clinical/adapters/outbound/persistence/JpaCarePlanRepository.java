package com.clinicflow.clinical.adapters.outbound.persistence;

import com.clinicflow.clinical.application.ports.CarePlanRepository;
import com.clinicflow.clinical.domain.CarePlan;
import com.clinicflow.clinical.domain.ClinicalCaseId;
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
    public void save(CarePlan carePlan) {
        delegate.save(mapper.toJpa(carePlan));
    }
}
