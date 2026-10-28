package com.clinicflow.clinical.application.queries;

import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.PsychologistId;

public record ListSessionRecordsQuery(PsychologistId psychologistId, ClinicalCaseId clinicalCaseId, PageQuery page) {
    public ListSessionRecordsQuery {
        if (psychologistId == null || clinicalCaseId == null || page == null) {
            throw new IllegalArgumentException("psychologistId, clinicalCaseId and page are required");
        }
    }
}
