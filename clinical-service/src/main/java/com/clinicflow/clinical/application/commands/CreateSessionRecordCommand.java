package com.clinicflow.clinical.application.commands;

import com.clinicflow.clinical.domain.AppointmentId;
import com.clinicflow.clinical.domain.AttendanceStatus;
import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.PsychologistId;
import com.clinicflow.clinical.domain.SessionModality;

import java.time.Instant;

public record CreateSessionRecordCommand(PsychologistId psychologistId, ClinicalCaseId clinicalCaseId,
                                         AppointmentId appointmentId, Instant sessionDate,
                                         SessionModality modality, int durationMinutes,
                                         AttendanceStatus attendanceStatus, String summary, String focusAreas,
                                         String interventions, String homework, String nextSteps) {
}
