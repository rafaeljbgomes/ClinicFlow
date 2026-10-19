package com.clinicflow.appointment.adapters.inbound.rest;

import com.clinicflow.appointment.adapters.inbound.rest.dto.AppointmentResponse;
import com.clinicflow.appointment.adapters.inbound.rest.dto.CancelAppointmentRequest;
import com.clinicflow.appointment.adapters.inbound.rest.dto.RescheduleAppointmentRequest;
import com.clinicflow.appointment.adapters.inbound.rest.dto.ScheduleAppointmentRequest;
import com.clinicflow.appointment.application.commands.CancelAppointmentCommand;
import com.clinicflow.appointment.application.commands.CompleteAppointmentCommand;
import com.clinicflow.appointment.application.commands.RescheduleAppointmentCommand;
import com.clinicflow.appointment.application.commands.ScheduleAppointmentCommand;
import com.clinicflow.appointment.application.mapping.AppointmentApplicationMapper;
import com.clinicflow.appointment.application.queries.GetAppointmentQuery;
import com.clinicflow.appointment.application.queries.ListAppointmentsQuery;
import com.clinicflow.appointment.application.results.AppointmentResult;
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
    private static final UUID PSYCHOLOGIST_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PATIENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID APPOINTMENT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private final AppointmentRestMapper restMapper = Mappers.getMapper(AppointmentRestMapper.class);
    private final AppointmentApplicationMapper applicationMapper =
            Mappers.getMapper(AppointmentApplicationMapper.class);

    @Test
    void scheduleRequestMapsToCommand() {
        // Arrange
        ScheduleAppointmentRequest request = new ScheduleAppointmentRequest(
                PATIENT_ID, Instant.now().plusSeconds(3600), AppointmentType.ONLINE);

        // Act
        ScheduleAppointmentCommand command = restMapper.toCommand(PSYCHOLOGIST_ID, request);

        // Assert
        assertThat(command.psychologistId()).isEqualTo(new PsychologistId(PSYCHOLOGIST_ID));
        assertThat(command.patientId()).isEqualTo(new PatientId(PATIENT_ID));
    }

    @Test
    void rescheduleRequestMapsToCommandForTheTargetAppointment() {
        // Arrange
        RescheduleAppointmentRequest request = new RescheduleAppointmentRequest(Instant.now().plusSeconds(7200));

        // Act
        RescheduleAppointmentCommand command = restMapper.toCommand(PSYCHOLOGIST_ID, APPOINTMENT_ID, request);

        // Assert
        assertThat(command.appointmentId()).isEqualTo(new AppointmentId(APPOINTMENT_ID));
    }

    @Test
    void cancelRequestMapsToCommandWithTrimmedReason() {
        // Arrange
        CancelAppointmentRequest request = new CancelAppointmentRequest("  Patient request ");

        // Act
        CancelAppointmentCommand command = restMapper.toCommand(PSYCHOLOGIST_ID, APPOINTMENT_ID, request);

        // Assert
        assertThat(command.reason().value()).isEqualTo("Patient request");
    }

    @Test
    void completeCommandTargetsTheRequestedAppointment() {
        // Act
        CompleteAppointmentCommand command = restMapper.toCompleteCommand(PSYCHOLOGIST_ID, APPOINTMENT_ID);

        // Assert
        assertThat(command.appointmentId()).isEqualTo(new AppointmentId(APPOINTMENT_ID));
    }

    @Test
    void getQueryTargetsTheRequestedAppointment() {
        // Act
        GetAppointmentQuery query = restMapper.toGetQuery(PSYCHOLOGIST_ID, APPOINTMENT_ID);

        // Assert
        assertThat(query.appointmentId()).isEqualTo(new AppointmentId(APPOINTMENT_ID));
    }

    @Test
    void listQueryTargetsTheAuthenticatedPsychologist() {
        // Act
        ListAppointmentsQuery query = restMapper.toListQuery(PSYCHOLOGIST_ID);

        // Assert
        assertThat(query.psychologistId()).isEqualTo(new PsychologistId(PSYCHOLOGIST_ID));
    }

    @Test
    void appointmentMapsToApplicationResult() {
        // Arrange
        Instant scheduledAt = Instant.now().plusSeconds(3600);
        Appointment appointment = Appointment.create(
                new AppointmentId(APPOINTMENT_ID),
                new PsychologistId(PSYCHOLOGIST_ID),
                new PatientId(PATIENT_ID),
                new AppointmentDate(scheduledAt),
                AppointmentType.ONLINE,
                Instant.now()
        );

        // Act
        AppointmentResult result = applicationMapper.toResult(appointment);

        // Assert
        assertThat(result.id()).isEqualTo(APPOINTMENT_ID);
        assertThat(result.scheduledAt()).isEqualTo(scheduledAt);
        assertThat(result.status()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void applicationResultMapsToResponse() {
        // Arrange
        Instant scheduledAt = Instant.parse("2099-06-12T10:00:00Z");
        AppointmentResult result = new AppointmentResult(APPOINTMENT_ID, PSYCHOLOGIST_ID, PATIENT_ID,
                scheduledAt, AppointmentStatus.SCHEDULED, AppointmentType.ONLINE, null);

        // Act
        AppointmentResponse response = restMapper.toResponse(result);

        // Assert
        assertThat(response.id()).isEqualTo(APPOINTMENT_ID);
        assertThat(response.scheduledAt()).isEqualTo(scheduledAt);
        assertThat(response.status()).isEqualTo(AppointmentStatus.SCHEDULED);
    }
}
