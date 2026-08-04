package com.clinicflow.clinical.application.results;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CarePlanResult(UUID id, UUID clinicalCaseId, UUID psychologistId, UUID patientId,
                             String therapeuticFocus, String plannedFrequency, LocalDate reviewDate,
                             List<CareGoalResult> goals, Instant createdAt, Instant updatedAt) {
}
