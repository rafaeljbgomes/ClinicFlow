package com.clinicflow.clinical.application.commands;

import com.clinicflow.clinical.domain.PsychologistId;
import com.clinicflow.clinical.domain.SessionRecordId;

public record SignSessionRecordCommand(PsychologistId psychologistId, SessionRecordId sessionRecordId) {
}
