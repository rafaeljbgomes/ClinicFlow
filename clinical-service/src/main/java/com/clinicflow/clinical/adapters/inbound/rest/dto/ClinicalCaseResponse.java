package com.clinicflow.clinical.adapters.inbound.rest.dto;

import com.clinicflow.clinical.domain.ClinicalCaseStatus;

import java.time.Instant;
import java.util.UUID;

public record ClinicalCaseResponse(UUID id, UUID psychologistId, UUID patientId, String presentingConcern,
                                   ClinicalCaseStatus status, Instant openedAt, Instant closedAt,
                                   Instant createdAt, Instant updatedAt) {
}
