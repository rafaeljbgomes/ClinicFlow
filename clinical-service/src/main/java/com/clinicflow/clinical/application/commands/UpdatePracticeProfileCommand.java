package com.clinicflow.clinical.application.commands;

import com.clinicflow.clinical.domain.PsychologistId;

import java.time.ZoneId;

public record UpdatePracticeProfileCommand(PsychologistId psychologistId, String professionalRegistration,
                                           ZoneId timezone, int defaultSessionDurationMinutes,
                                           String primaryLocation, boolean telehealthEnabled) {
}
