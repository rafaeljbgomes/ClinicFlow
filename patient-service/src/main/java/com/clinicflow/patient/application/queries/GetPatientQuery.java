package com.clinicflow.patient.application.queries;

import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PsychologistId;

public record GetPatientQuery(PsychologistId psychologistId, PatientId patientId) {
}
