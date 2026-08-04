package com.clinicflow.clinical.application.results;

import com.clinicflow.clinical.domain.AttendanceStatus;
import com.clinicflow.clinical.domain.NoteStatus;
import com.clinicflow.clinical.domain.SessionModality;

import java.time.Instant;
import java.util.UUID;

public record SessionRecordResult(UUID id, UUID clinicalCaseId, UUID psychologistId, UUID patientId,
                                  UUID appointmentId, Instant sessionDate, SessionModality modality,
                                  int durationMinutes, AttendanceStatus attendanceStatus, NoteStatus noteStatus,
                                  String summary, String focusAreas, String interventions, String homework,
                                  String nextSteps, Instant createdAt, Instant updatedAt) {
}
