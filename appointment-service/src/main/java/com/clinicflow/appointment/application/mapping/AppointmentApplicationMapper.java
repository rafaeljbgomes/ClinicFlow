package com.clinicflow.appointment.application.mapping;

import com.clinicflow.appointment.application.results.AppointmentResult;
import com.clinicflow.appointment.domain.Appointment;
import org.mapstruct.Mapper;

@Mapper(config = MapperConfiguration.class)
public abstract class AppointmentApplicationMapper {
    public abstract String copy(String value);

    public AppointmentResult toResult(Appointment appointment) {
        return new AppointmentResult(
                appointment.id().value(),
                appointment.psychologistId().value(),
                appointment.patientId().value(),
                appointment.scheduledAt().value(),
                appointment.status(),
                appointment.type(),
                appointment.cancellationReason().map(reason -> reason.value()).orElse(null)
        );
    }
}
