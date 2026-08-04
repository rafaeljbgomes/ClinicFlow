package com.clinicflow.clinical.application.queries;

import com.clinicflow.clinical.domain.ClinicalCaseStatus;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PsychologistId;

public record ListClinicalCasesQuery(PsychologistId psychologistId, PatientId patientId,
                                     ClinicalCaseStatus status) {
}
