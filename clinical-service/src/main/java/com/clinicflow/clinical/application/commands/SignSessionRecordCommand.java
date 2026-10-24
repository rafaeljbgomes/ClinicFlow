package com.clinicflow.clinical.application.commands;

import com.clinicflow.clinical.domain.PsychologistId;
import com.clinicflow.clinical.domain.SessionRecordId;

// The expected version binds the signature to the revision the psychologist reviewed.
public record SignSessionRecordCommand(PsychologistId psychologistId, SessionRecordId sessionRecordId,
                                       long expectedVersion) {
}
