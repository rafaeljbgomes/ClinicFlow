package com.clinicflow.clinical.application.ports;

import com.clinicflow.clinical.application.queries.ListClinicalCasesQuery;
import com.clinicflow.clinical.application.results.PageResult;
import com.clinicflow.clinical.domain.ClinicalCase;
import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PsychologistId;

import java.util.List;
import java.util.Optional;

public interface ClinicalCaseRepository {
    Optional<ClinicalCase> findById(ClinicalCaseId id);
    // Pages list the most recently opened case first.
    PageResult<ClinicalCase> findPage(ListClinicalCasesQuery query);
    List<ClinicalCase> findByPsychologistIdAndPatientId(PsychologistId psychologistId, PatientId patientId);
    Optional<ClinicalCase> findOpenByPsychologistIdAndPatientId(PsychologistId psychologistId, PatientId patientId);
    boolean existsOpenByPsychologistIdAndPatientId(PsychologistId psychologistId, PatientId patientId);
    // Returns the stored case, including the version persistence assigned.
    ClinicalCase save(ClinicalCase clinicalCase);
}
