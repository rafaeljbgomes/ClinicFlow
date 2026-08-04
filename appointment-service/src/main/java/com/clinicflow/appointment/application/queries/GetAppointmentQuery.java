package com.clinicflow.appointment.application.queries;

import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.PsychologistId;

public record GetAppointmentQuery(PsychologistId psychologistId, AppointmentId appointmentId) {
}
