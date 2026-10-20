package com.clinicflow.appointment.application.ports;

import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;

public interface PatientDirectory {
    /**
     * Asks the patient service whether the patient belongs to the psychologist.
     *
     * @throws com.clinicflow.appointment.application.exceptions.OwnershipCheckUnavailableException
     *         when ownership cannot be decided, so callers fail closed
     */
    boolean isOwnedBy(PatientId patientId, PsychologistId psychologistId);
}
