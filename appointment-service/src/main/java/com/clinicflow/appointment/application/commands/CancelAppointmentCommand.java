package com.clinicflow.appointment.application.commands;

import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.CancellationReason;
import com.clinicflow.appointment.domain.PsychologistId;

public record CancelAppointmentCommand(PsychologistId psychologistId, AppointmentId appointmentId,
                                       CancellationReason reason) {
}
