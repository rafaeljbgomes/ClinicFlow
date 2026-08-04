package com.clinicflow.appointment.adapters.outbound.persistence;

import com.clinicflow.appointment.application.mapping.MapperConfiguration;
import com.clinicflow.appointment.domain.Appointment;
import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.CancellationReason;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;
import org.mapstruct.Mapper;

@Mapper(config = MapperConfiguration.class)
public abstract class AppointmentPersistenceMapper {
    public abstract String copy(String value);

    public JpaAppointmentEntity toJpa(Appointment appointment) {
        return new JpaAppointmentEntity(
                appointment.id().value(),
                appointment.psychologistId().value(),
                appointment.patientId().value(),
                appointment.scheduledAt().value(),
                appointment.status(),
                appointment.type(),
                appointment.cancellationReason().map(reason -> reason.value()).orElse(null),
                appointment.createdAt(),
                appointment.updatedAt()
        );
    }

    public Appointment toDomain(JpaAppointmentEntity entity) {
        return Appointment.rehydrate(
                new AppointmentId(entity.id()),
                new PsychologistId(entity.psychologistId()),
                new PatientId(entity.patientId()),
                new AppointmentDate(entity.scheduledAt()),
                entity.status(),
                entity.type(),
                entity.cancellationReason() == null ? null : new CancellationReason(entity.cancellationReason()),
                entity.createdAt(),
                entity.updatedAt()
        );
    }
}
