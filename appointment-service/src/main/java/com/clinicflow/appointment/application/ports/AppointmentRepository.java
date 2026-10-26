package com.clinicflow.appointment.application.ports;

import com.clinicflow.appointment.application.queries.ListAppointmentsQuery;
import com.clinicflow.appointment.application.results.PageResult;
import com.clinicflow.appointment.domain.Appointment;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.PatientId;

import java.util.List;
import java.util.Optional;

public interface AppointmentRepository {
    // Returns the stored appointment, including the version persistence assigned.
    Appointment save(Appointment appointment);
    Optional<Appointment> findById(AppointmentId id);
    // Pages follow the schedule order the query asks for.
    PageResult<Appointment> findPage(ListAppointmentsQuery query);
    List<Appointment> findByPatientId(PatientId patientId);
}
