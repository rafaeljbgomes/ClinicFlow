package com.clinicflow.clinical.application.ports;

import com.clinicflow.clinical.domain.CarePlan;
import com.clinicflow.clinical.domain.ClinicalCaseId;

import java.util.Optional;

public interface CarePlanRepository {
    Optional<CarePlan> findByClinicalCaseId(ClinicalCaseId clinicalCaseId);
    // Returns the stored plan, including the version persistence assigned.
    CarePlan save(CarePlan carePlan);
}
