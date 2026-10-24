package com.clinicflow.clinical.domain;

import java.time.Instant;

// Audit entry for an appointment link released from a session record that did not belong to the appointment's
// owner. It keeps the released id, so the record itself never has to show it again.
public record SessionRecordLinkConflict(SessionRecordLinkConflictId id, SessionRecordId sessionRecordId,
                                        AppointmentId appointmentId, PsychologistId appointmentPsychologistId,
                                        PatientId appointmentPatientId, LinkConflictSource source,
                                        Instant detectedAt) {
    public SessionRecordLinkConflict {
        if (id == null || sessionRecordId == null || appointmentId == null || appointmentPsychologistId == null
                || appointmentPatientId == null || source == null || detectedAt == null) {
            throw new IllegalArgumentException("Session record link conflict fields are missing");
        }
    }
}
