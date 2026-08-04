package com.clinicflow.clinical.application.commands;

import com.clinicflow.clinical.domain.AttendanceStatus;
import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.NoteStatus;
import com.clinicflow.clinical.domain.PsychologistId;
import com.clinicflow.clinical.domain.SessionModality;
import com.clinicflow.clinical.domain.SessionRecordId;

import java.time.Instant;

public record UpdateSessionRecordCommand(PsychologistId psychologistId, SessionRecordId sessionRecordId,
                                         ClinicalCaseId clinicalCaseId, Instant sessionDate,
                                         SessionModality modality, int durationMinutes,
                                         AttendanceStatus attendanceStatus, NoteStatus noteStatus,
                                         String summary, String focusAreas, String interventions,
                                         String homework, String nextSteps) {
}
