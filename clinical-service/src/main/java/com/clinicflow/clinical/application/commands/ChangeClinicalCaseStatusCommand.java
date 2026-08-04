package com.clinicflow.clinical.application.commands;

import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.ClinicalCaseStatus;
import com.clinicflow.clinical.domain.PsychologistId;

public record ChangeClinicalCaseStatusCommand(PsychologistId psychologistId, ClinicalCaseId clinicalCaseId,
                                              ClinicalCaseStatus status) {
}
