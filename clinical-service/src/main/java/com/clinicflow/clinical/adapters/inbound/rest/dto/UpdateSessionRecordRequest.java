package com.clinicflow.clinical.adapters.inbound.rest.dto;

import com.clinicflow.clinical.domain.AttendanceStatus;
import com.clinicflow.clinical.domain.NoteStatus;
import com.clinicflow.clinical.domain.SessionModality;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record UpdateSessionRecordRequest(
        UUID clinicalCaseId,
        @NotNull Instant sessionDate,
        @NotNull SessionModality modality,
        @Min(0) @Max(480) int durationMinutes,
        @NotNull AttendanceStatus attendanceStatus,
        @NotNull NoteStatus noteStatus,
        @Size(max = 2000) String summary,
        @Size(max = 1000) String focusAreas,
        @Size(max = 1000) String interventions,
        @Size(max = 1000) String homework,
        @Size(max = 1000) String nextSteps
) {
}
