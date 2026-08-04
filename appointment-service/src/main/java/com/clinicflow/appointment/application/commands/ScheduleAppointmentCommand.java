package com.clinicflow.appointment.application.commands;

import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentType;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;

public record ScheduleAppointmentCommand(PsychologistId psychologistId, PatientId patientId,
                                         AppointmentDate scheduledAt, AppointmentType type) {
}
