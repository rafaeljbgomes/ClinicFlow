package com.clinicflow.clinical.application.queries;

import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.PsychologistId;

public record ListSessionRecordsQuery(PsychologistId psychologistId, ClinicalCaseId clinicalCaseId) {
}
