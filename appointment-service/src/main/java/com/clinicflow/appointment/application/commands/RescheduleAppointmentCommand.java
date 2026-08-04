package com.clinicflow.appointment.application.commands;

import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.PsychologistId;

public record RescheduleAppointmentCommand(PsychologistId psychologistId, AppointmentId appointmentId,
                                           AppointmentDate newDate) {
}
