package com.clinicflow.appointment.adapters.outbound.messaging;

import com.clinicflow.appointment.application.mapping.MapperConfiguration;
import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.CancellationActor;
import com.clinicflow.appointment.domain.CancellationReason;
import com.clinicflow.appointment.domain.DomainEventId;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;
import com.clinicflow.appointment.domain.events.AppointmentCancelledEvent;
import com.clinicflow.appointment.domain.events.AppointmentCompletedEvent;
import com.clinicflow.appointment.domain.events.AppointmentRescheduledEvent;
import com.clinicflow.appointment.domain.events.AppointmentScheduledEvent;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.util.UUID;

@Mapper(config = MapperConfiguration.class)
public interface AppointmentEventMapper {
    AppointmentScheduledPayload toPayload(AppointmentScheduledEvent event);
    AppointmentRescheduledPayload toPayload(AppointmentRescheduledEvent event);
    AppointmentCancelledPayload toPayload(AppointmentCancelledEvent event);
    AppointmentCompletedPayload toPayload(AppointmentCompletedEvent event);

    default UUID map(DomainEventId id) { return id.value(); }
    default UUID map(AppointmentId id) { return id.value(); }
    default UUID map(PatientId id) { return id.value(); }
    default UUID map(PsychologistId id) { return id.value(); }
    default Instant map(AppointmentDate date) { return date.value(); }
    default String map(CancellationActor actor) { return actor.value(); }
    default String map(CancellationReason reason) { return reason.value(); }
}
