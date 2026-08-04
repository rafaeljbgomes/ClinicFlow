package com.clinicflow.patient.application.ports;

import com.clinicflow.patient.domain.Patient;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PsychologistId;

import java.util.List;
import java.util.Optional;

public interface PatientRepository {
    void save(Patient patient);
    Optional<Patient> findById(PatientId id);
    List<Patient> findByPsychologistId(PsychologistId psychologistId);
}
