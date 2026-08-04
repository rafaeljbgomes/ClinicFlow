package com.clinicflow.clinical.application.queries;

import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.PsychologistId;

public record GetClinicalCaseQuery(PsychologistId psychologistId, ClinicalCaseId clinicalCaseId) {
}
