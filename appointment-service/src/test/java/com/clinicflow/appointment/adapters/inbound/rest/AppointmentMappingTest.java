package com.clinicflow.appointment.adapters.inbound.rest;

import com.clinicflow.appointment.adapters.inbound.rest.dto.CancelAppointmentRequest;
import com.clinicflow.appointment.adapters.inbound.rest.dto.RescheduleAppointmentRequest;
import com.clinicflow.appointment.adapters.inbound.rest.dto.ScheduleAppointmentRequest;
import com.clinicflow.appointment.application.mapping.AppointmentApplicationMapper;
import com.clinicflow.appointment.domain.Appointment;
import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.AppointmentStatus;
import com.clinicflow.appointment.domain.AppointmentType;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentMappingTest {
    private final AppointmentRestMapper restMapper = Mappers.getMapper(AppointmentRestMapper.class);
    private final AppointmentApplicationMapper applicationMapper =
            Mappers.getMapper(AppointmentApplicationMapper.class);

    @Test
    void mapsRestInputsAndApplicationOutputs() {
        UUID psychologistId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Instant scheduledAt = Instant.now().plusSeconds(3600);

        var scheduleCommand = restMapper.toCommand(
                psychologistId,
                new ScheduleAppointmentRequest(patientId, scheduledAt, AppointmentType.ONLINE)
        );
        var rescheduleCommand = restMapper.toCommand(
                psychologistId,
                appointmentId,
                new RescheduleAppointmentRequest(scheduledAt.plusSeconds(3600))
        );
        var cancelCommand = restMapper.toCommand(
                psychologistId,
                appointmentId,
                new CancelAppointmentRequest("  Patient request ")
        );

        assertThat(scheduleCommand.psychologistId()).isEqualTo(new PsychologistId(psychologistId));
        assertThat(scheduleCommand.patientId()).isEqualTo(new PatientId(patientId));
        assertThat(rescheduleCommand.appointmentId()).isEqualTo(new AppointmentId(appointmentId));
        assertThat(cancelCommand.reason().value()).isEqualTo("Patient request");
        assertThat(restMapper.toCompleteCommand(psychologistId, appointmentId).appointmentId())
                .isEqualTo(new AppointmentId(appointmentId));
        assertThat(restMapper.toGetQuery(psychologistId, appointmentId).appointmentId())
                .isEqualTo(new AppointmentId(appointmentId));
        assertThat(restMapper.toListQuery(psychologistId).psychologistId())
                .isEqualTo(new PsychologistId(psychologistId));

        Appointment appointment = Appointment.create(
                new AppointmentId(appointmentId),
                scheduleCommand.psychologistId(),
                scheduleCommand.patientId(),
                new AppointmentDate(scheduledAt),
                scheduleCommand.type(),
                Instant.now()
        );

        var response = restMapper.toResponse(applicationMapper.toResult(appointment));

        assertThat(response.id()).isEqualTo(appointmentId);
        assertThat(response.scheduledAt()).isEqualTo(scheduledAt);
        assertThat(response.status()).isEqualTo(AppointmentStatus.SCHEDULED);
    }
}
