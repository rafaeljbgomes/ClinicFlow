package com.clinicflow.clinical.adapters.inbound.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record PracticeProfileResponse(UUID psychologistId, String professionalRegistration, String timezone,
                                      int defaultSessionDurationMinutes, String primaryLocation,
                                      boolean telehealthEnabled, Instant createdAt, Instant updatedAt) {
}
