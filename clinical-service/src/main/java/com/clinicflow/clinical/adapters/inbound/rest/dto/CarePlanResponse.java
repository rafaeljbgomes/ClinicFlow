package com.clinicflow.clinical.adapters.inbound.rest.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CarePlanResponse(UUID id, UUID clinicalCaseId, UUID psychologistId, UUID patientId,
                               String therapeuticFocus, String plannedFrequency, LocalDate reviewDate,
                               List<CareGoalResponse> goals, Instant createdAt, Instant updatedAt) {
}
