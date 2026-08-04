package com.clinicflow.clinical.application.ports;

import com.clinicflow.clinical.domain.CarePlan;
import com.clinicflow.clinical.domain.ClinicalCaseId;

import java.util.Optional;

public interface CarePlanRepository {
    Optional<CarePlan> findByClinicalCaseId(ClinicalCaseId clinicalCaseId);
    void save(CarePlan carePlan);
}
