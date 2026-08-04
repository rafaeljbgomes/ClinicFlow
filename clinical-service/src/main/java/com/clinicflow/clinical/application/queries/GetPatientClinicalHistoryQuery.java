package com.clinicflow.clinical.application.queries;

import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PsychologistId;

public record GetPatientClinicalHistoryQuery(PsychologistId psychologistId, PatientId patientId) {
}
