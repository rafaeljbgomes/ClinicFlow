package com.clinicflow.appointment.application.ports;

import com.clinicflow.appointment.domain.Appointment;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;

import java.util.List;
import java.util.Optional;

public interface AppointmentRepository {
    void save(Appointment appointment);
    Optional<Appointment> findById(AppointmentId id);
    List<Appointment> findByPsychologistId(PsychologistId psychologistId);
    List<Appointment> findByPatientId(PatientId patientId);
}
