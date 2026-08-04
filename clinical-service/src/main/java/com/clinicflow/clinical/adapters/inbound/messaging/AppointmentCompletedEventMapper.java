package com.clinicflow.clinical.adapters.inbound.messaging;

import com.clinicflow.clinical.application.commands.ProcessAppointmentCompletedCommand;
import com.clinicflow.clinical.application.mapping.MapperConfiguration;
import com.clinicflow.clinical.domain.AppointmentId;
import com.clinicflow.clinical.domain.DomainEventId;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PsychologistId;
import com.clinicflow.clinical.domain.SessionModality;
import org.mapstruct.Mapper;

@Mapper(config = MapperConfiguration.class)
public abstract class AppointmentCompletedEventMapper {
    public ProcessAppointmentCompletedCommand toCommand(InboundAppointmentCompletedEvent event) {
        return new ProcessAppointmentCompletedCommand(
                new DomainEventId(event.eventId()),
                new AppointmentId(event.appointmentId()),
                new PatientId(event.patientId()),
                new PsychologistId(event.psychologistId()),
                event.appointmentDate(),
                modality(event.appointmentType())
        );
    }

    private SessionModality modality(String appointmentType) {
        return "ONLINE".equalsIgnoreCase(appointmentType) ? SessionModality.ONLINE : SessionModality.IN_PERSON;
    }
}
