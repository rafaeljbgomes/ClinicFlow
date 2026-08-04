package com.clinicflow.clinical.adapters.inbound.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PracticeProfileRequest(
        @Size(max = 80) String professionalRegistration,
        @NotBlank @Size(max = 80) String timezone,
        @Min(15) @Max(240) int defaultSessionDurationMinutes,
        @Size(max = 160) String primaryLocation,
        boolean telehealthEnabled
) {
}
