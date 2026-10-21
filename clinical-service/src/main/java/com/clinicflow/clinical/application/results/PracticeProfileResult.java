package com.clinicflow.clinical.application.results;

import java.time.Instant;
import java.util.UUID;

public record PracticeProfileResult(UUID psychologistId, String professionalRegistration, String timezone,
                                    int defaultSessionDurationMinutes, String primaryLocation,
                                    boolean telehealthEnabled, Instant createdAt, Instant updatedAt,
                                    Long version) {
    // The version is null for the default profile shown before the psychologist first saves one.
}
