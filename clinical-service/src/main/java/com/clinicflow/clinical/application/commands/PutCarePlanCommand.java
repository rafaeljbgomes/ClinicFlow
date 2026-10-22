package com.clinicflow.clinical.application.commands;

import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.PsychologistId;

import java.time.LocalDate;
import java.util.List;

public record PutCarePlanCommand(PsychologistId psychologistId, ClinicalCaseId clinicalCaseId,
                                 String therapeuticFocus, String plannedFrequency, LocalDate reviewDate,
                                 List<CareGoalInput> goals, Long expectedVersion) {
    // A null expected version means the caller expects to create the plan (If-None-Match: *).
}
