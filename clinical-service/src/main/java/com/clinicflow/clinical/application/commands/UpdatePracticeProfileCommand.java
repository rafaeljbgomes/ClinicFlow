package com.clinicflow.clinical.application.commands;

import com.clinicflow.clinical.domain.PsychologistId;

import java.time.ZoneId;

public record UpdatePracticeProfileCommand(PsychologistId psychologistId, String professionalRegistration,
                                           ZoneId timezone, int defaultSessionDurationMinutes,
                                           String primaryLocation, boolean telehealthEnabled,
                                           Long expectedVersion) {
    // A null expected version means the caller expects to create the profile (If-None-Match: *).
}
