package com.clinicflow.clinical.application.ports;

import com.clinicflow.clinical.domain.AppointmentId;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PsychologistId;

import java.util.Optional;

public interface AppointmentDirectory {
    /**
     * Returns the patient of an appointment owned by the psychologist, or empty when the appointment is
     * unknown or belongs to someone else.
     *
     * @throws com.clinicflow.clinical.application.exceptions.OwnershipCheckUnavailableException
     *         when ownership cannot be decided, so callers fail closed
     */
    Optional<PatientId> findPatientOfOwnedAppointment(AppointmentId appointmentId, PsychologistId psychologistId);
}
