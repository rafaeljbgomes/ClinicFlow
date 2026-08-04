package com.clinicflow.appointment.adapters.inbound.rest;

import com.clinicflow.appointment.adapters.inbound.rest.dto.AppointmentResponse;
import com.clinicflow.appointment.adapters.inbound.rest.dto.CancelAppointmentRequest;
import com.clinicflow.appointment.adapters.inbound.rest.dto.RescheduleAppointmentRequest;
import com.clinicflow.appointment.adapters.inbound.rest.dto.ScheduleAppointmentRequest;
import com.clinicflow.appointment.application.commands.CancelAppointmentCommand;
import com.clinicflow.appointment.application.commands.CompleteAppointmentCommand;
import com.clinicflow.appointment.application.commands.RescheduleAppointmentCommand;
import com.clinicflow.appointment.application.commands.ScheduleAppointmentCommand;
import com.clinicflow.appointment.application.mapping.MapperConfiguration;
import com.clinicflow.appointment.application.queries.GetAppointmentQuery;
import com.clinicflow.appointment.application.queries.ListAppointmentsQuery;
import com.clinicflow.appointment.application.results.AppointmentResult;
import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.CancellationReason;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;
import org.mapstruct.Mapper;

import java.util.UUID;

@Mapper(config = MapperConfiguration.class)
public abstract class AppointmentRestMapper {
    public ScheduleAppointmentCommand toCommand(UUID psychologistId, ScheduleAppointmentRequest request) {
        return new ScheduleAppointmentCommand(new PsychologistId(psychologistId),
                new PatientId(request.patientId()), new AppointmentDate(request.scheduledAt()), request.type());
    }

    public RescheduleAppointmentCommand toCommand(UUID psychologistId, UUID appointmentId,
                                                  RescheduleAppointmentRequest request) {
        return new RescheduleAppointmentCommand(new PsychologistId(psychologistId),
                new AppointmentId(appointmentId), new AppointmentDate(request.newDate()));
    }

    public CancelAppointmentCommand toCommand(UUID psychologistId, UUID appointmentId,
                                              CancelAppointmentRequest request) {
        return new CancelAppointmentCommand(new PsychologistId(psychologistId),
                new AppointmentId(appointmentId), new CancellationReason(request.reason()));
    }

    public CompleteAppointmentCommand toCompleteCommand(UUID psychologistId, UUID appointmentId) {
        return new CompleteAppointmentCommand(new PsychologistId(psychologistId), new AppointmentId(appointmentId));
    }

    public GetAppointmentQuery toGetQuery(UUID psychologistId, UUID appointmentId) {
        return new GetAppointmentQuery(new PsychologistId(psychologistId), new AppointmentId(appointmentId));
    }

    public ListAppointmentsQuery toListQuery(UUID psychologistId) {
        return new ListAppointmentsQuery(new PsychologistId(psychologistId));
    }

    public abstract AppointmentResponse toResponse(AppointmentResult result);
}
