package com.clinicflow.clinical.adapters.inbound.rest.dto;

import com.clinicflow.clinical.domain.ClinicalCaseStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeClinicalCaseStatusRequest(@NotNull ClinicalCaseStatus status) {
}
