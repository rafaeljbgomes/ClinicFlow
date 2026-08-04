package com.clinicflow.patient.application.commands;

import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PatientStatus;
import com.clinicflow.patient.domain.PsychologistId;

public record ChangePatientStatusCommand(PsychologistId psychologistId, PatientId patientId, PatientStatus status) {
}
