package com.clinicflow.appointment.application.commands;

import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.PsychologistId;

public record CompleteAppointmentCommand(PsychologistId psychologistId, AppointmentId appointmentId) {
}
