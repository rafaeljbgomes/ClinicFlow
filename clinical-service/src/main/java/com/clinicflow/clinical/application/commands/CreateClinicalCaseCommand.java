package com.clinicflow.clinical.application.commands;

import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PsychologistId;

public record CreateClinicalCaseCommand(PsychologistId psychologistId, PatientId patientId,
                                        String presentingConcern) {
}
