package com.clinicflow.clinical.application.queries;

import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.PsychologistId;

public record GetCarePlanQuery(PsychologistId psychologistId, ClinicalCaseId clinicalCaseId) {
}
