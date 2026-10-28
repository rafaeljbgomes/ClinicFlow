package com.clinicflow.clinical.application.queries;

import com.clinicflow.clinical.domain.ClinicalCaseStatus;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PsychologistId;

import java.util.Set;

// A null patientId means every patient, and an empty status set means every status.
public record ListClinicalCasesQuery(PsychologistId psychologistId, PatientId patientId,
                                     Set<ClinicalCaseStatus> statuses, PageQuery page) {
    public ListClinicalCasesQuery {
        if (psychologistId == null || page == null) {
            throw new IllegalArgumentException("psychologistId and page are required");
        }
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
    }
}
