package com.clinicflow.clinical.application.ports;

import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PsychologistId;

public interface PatientDirectory {
    /**
     * Asks the patient service whether the patient belongs to the psychologist.
     *
     * @throws com.clinicflow.clinical.application.exceptions.OwnershipCheckUnavailableException
     *         when ownership cannot be decided, so callers fail closed
     */
    boolean isOwnedBy(PatientId patientId, PsychologistId psychologistId);
}
