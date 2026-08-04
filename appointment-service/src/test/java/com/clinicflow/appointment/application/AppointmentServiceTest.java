package com.clinicflow.appointment.application;

import com.clinicflow.appointment.application.commands.CancelAppointmentCommand;
import com.clinicflow.appointment.application.commands.CompleteAppointmentCommand;
import com.clinicflow.appointment.application.commands.RescheduleAppointmentCommand;
import com.clinicflow.appointment.application.commands.ScheduleAppointmentCommand;
import com.clinicflow.appointment.application.exceptions.AppointmentAccessDeniedException;
import com.clinicflow.appointment.application.exceptions.AppointmentNotFoundException;
import com.clinicflow.appointment.application.mapping.AppointmentApplicationMapper;
import com.clinicflow.appointment.application.ports.AppointmentEventPublisher;
import com.clinicflow.appointment.application.ports.AppointmentRepository;
import com.clinicflow.appointment.application.queries.GetAppointmentQuery;
import com.clinicflow.appointment.application.queries.ListAppointmentsQuery;
import com.clinicflow.appointment.domain.Appointment;
import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.AppointmentStatus;
import com.clinicflow.appointment.domain.AppointmentType;
import com.clinicflow.appointment.domain.CancellationReason;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;
import com.clinicflow.appointment.domain.events.AppointmentCancelledEvent;
import com.clinicflow.appointment.domain.events.AppointmentCompletedEvent;
import com.clinicflow.appointment.domain.events.AppointmentRescheduledEvent;
import com.clinicflow.appointment.domain.events.AppointmentScheduledEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {
    private static final PsychologistId OWNER =
            new PsychologistId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private static final PsychologistId OTHER =
            new PsychologistId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    private static final AppointmentId APPOINTMENT_ID =
            new AppointmentId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));
    private static final PatientId PATIENT_ID =
            new PatientId(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"));
    private static final Instant CREATED_AT = Instant.parse("2026-06-01T10:00:00Z");

    @Mock
    private AppointmentRepository repository;
    @Mock
    private AppointmentEventPublisher publisher;

    private AppointmentService service;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(repository, publisher,
                Mappers.getMapper(AppointmentApplicationMapper.class));
    }

    @Test
    void schedulesAppointmentThenPublishesEvent() {
        AppointmentDate date = new AppointmentDate(Instant.now().plusSeconds(3600));
        ScheduleAppointmentCommand command =
                new ScheduleAppointmentCommand(OWNER, PATIENT_ID, date, AppointmentType.ONLINE);

        var result = service.schedule(command);

        ArgumentCaptor<Appointment> appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
        ArgumentCaptor<AppointmentScheduledEvent> eventCaptor =
                ArgumentCaptor.forClass(AppointmentScheduledEvent.class);
        InOrder order = inOrder(repository, publisher);
        order.verify(repository).save(appointmentCaptor.capture());
        order.verify(publisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().appointmentId()).isEqualTo(appointmentCaptor.getValue().id());
        assertThat(result.status()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void listsAndGetsOwnedAppointments() {
        when(repository.findByPsychologistId(OWNER)).thenReturn(List.of(appointment()));
        when(repository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment()));

        assertThat(service.listForPsychologist(new ListAppointmentsQuery(OWNER)))
                .extracting(result -> result.id())
                .containsExactly(APPOINTMENT_ID.value());
        assertThat(service.get(new GetAppointmentQuery(OWNER, APPOINTMENT_ID)).id())
                .isEqualTo(APPOINTMENT_ID.value());
    }

    @Test
    void reschedulesAndPublishesPreviousAndNewDates() {
        Appointment appointment = appointment();
        AppointmentDate previousDate = appointment.scheduledAt();
        AppointmentDate newDate = new AppointmentDate(Instant.now().plusSeconds(7200));
        when(repository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

        var result = service.reschedule(new RescheduleAppointmentCommand(OWNER, APPOINTMENT_ID, newDate));

        verify(repository).save(appointment);
        ArgumentCaptor<AppointmentRescheduledEvent> captor =
                ArgumentCaptor.forClass(AppointmentRescheduledEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().previousDate()).isEqualTo(previousDate);
        assertThat(captor.getValue().newDate()).isEqualTo(newDate);
        assertThat(result.status()).isEqualTo(AppointmentStatus.RESCHEDULED);
    }

    @Test
    void cancelsAndPublishesReason() {
        Appointment appointment = appointment();
        CancellationReason reason = new CancellationReason("Patient requested cancellation");
        when(repository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

        var result = service.cancel(new CancelAppointmentCommand(OWNER, APPOINTMENT_ID, reason));

        verify(repository).save(appointment);
        ArgumentCaptor<AppointmentCancelledEvent> captor =
                ArgumentCaptor.forClass(AppointmentCancelledEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().reason()).isEqualTo(reason);
        assertThat(captor.getValue().cancelledBy().value()).isEqualTo("psychologist");
        assertThat(result.status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void completesAndPublishesEvent() {
        Appointment appointment = appointment();
        when(repository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

        var result = service.complete(new CompleteAppointmentCommand(OWNER, APPOINTMENT_ID));

        verify(repository).save(appointment);
        ArgumentCaptor<AppointmentCompletedEvent> captor =
                ArgumentCaptor.forClass(AppointmentCompletedEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().appointmentId()).isEqualTo(APPOINTMENT_ID);
        assertThat(captor.getValue().patientId()).isEqualTo(PATIENT_ID);
        assertThat(captor.getValue().psychologistId()).isEqualTo(OWNER);
        assertThat(captor.getValue().appointmentType()).isEqualTo(AppointmentType.IN_PERSON);
        assertThat(result.status()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void rejectsMissingAppointment() {
        when(repository.findById(APPOINTMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(new GetAppointmentQuery(OWNER, APPOINTMENT_ID)))
                .isInstanceOf(AppointmentNotFoundException.class);
    }

    @Test
    void rejectsAppointmentOwnedByAnotherPsychologistWithoutMutation() {
        Appointment appointment = appointment();
        when(repository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.complete(new CompleteAppointmentCommand(OTHER, APPOINTMENT_ID)))
                .isInstanceOf(AppointmentAccessDeniedException.class);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(appointment.status()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    private Appointment appointment() {
        return Appointment.create(APPOINTMENT_ID, OWNER, PATIENT_ID,
                new AppointmentDate(Instant.now().plusSeconds(3600)),
                AppointmentType.IN_PERSON, CREATED_AT);
    }
}
