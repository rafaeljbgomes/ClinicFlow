package com.clinicflow.clinical.application.commands;

import com.clinicflow.clinical.domain.AppointmentId;
import com.clinicflow.clinical.domain.DomainEventId;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PsychologistId;
import com.clinicflow.clinical.domain.SessionModality;

import java.time.Instant;

public record ProcessAppointmentCompletedCommand(DomainEventId eventId, AppointmentId appointmentId,
                                                 PatientId patientId, PsychologistId psychologistId,
                                                 Instant appointmentDate, SessionModality modality) {
}
