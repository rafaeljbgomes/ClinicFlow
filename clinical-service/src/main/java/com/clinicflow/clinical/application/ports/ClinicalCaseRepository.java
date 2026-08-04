package com.clinicflow.clinical.application.ports;

import com.clinicflow.clinical.domain.ClinicalCase;
import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.ClinicalCaseStatus;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PsychologistId;

import java.util.List;
import java.util.Optional;

public interface ClinicalCaseRepository {
    Optional<ClinicalCase> findById(ClinicalCaseId id);
    List<ClinicalCase> findByPsychologistId(PsychologistId psychologistId);
    List<ClinicalCase> findByPsychologistIdAndPatientId(PsychologistId psychologistId, PatientId patientId);
    List<ClinicalCase> findByPsychologistIdAndStatus(PsychologistId psychologistId, ClinicalCaseStatus status);
    Optional<ClinicalCase> findOpenByPsychologistIdAndPatientId(PsychologistId psychologistId, PatientId patientId);
    boolean existsOpenByPsychologistIdAndPatientId(PsychologistId psychologistId, PatientId patientId);
    void save(ClinicalCase clinicalCase);
}
