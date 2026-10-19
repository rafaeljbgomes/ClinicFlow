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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
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
    private static final CancellationReason REASON = new CancellationReason("Patient requested cancellation");

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
    void scheduleSavesAppointmentBeforePublishingEvent() {
        // Arrange
        ScheduleAppointmentCommand command = scheduleCommand();

        // Act
        service.schedule(command);

        // Assert
        InOrder order = inOrder(repository, publisher);
        order.verify(repository).save(any(Appointment.class));
        order.verify(publisher).publish(any(AppointmentScheduledEvent.class));
    }

    @Test
    void schedulePublishesEventForTheSavedAppointment() {
        // Arrange
        ScheduleAppointmentCommand command = scheduleCommand();

        // Act
        service.schedule(command);

        // Assert
        AppointmentScheduledEvent event = publishedScheduledEvent();
        assertThat(event.appointmentId()).isEqualTo(savedAppointment().id());
    }

    @Test
    void scheduleReturnsScheduledAppointment() {
        // Arrange
        ScheduleAppointmentCommand command = scheduleCommand();

        // Act
        var result = service.schedule(command);

        // Assert
        assertThat(result.status()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void listReturnsOwnedAppointments() {
        // Arrange
        when(repository.findByPsychologistId(OWNER)).thenReturn(List.of(appointment()));

        // Act
        var results = service.listForPsychologist(new ListAppointmentsQuery(OWNER));

        // Assert
        assertThat(results).extracting(result -> result.id()).containsExactly(APPOINTMENT_ID.value());
    }

    @Test
    void getReturnsOwnedAppointment() {
        // Arrange
        when(repository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment()));

        // Act
        var result = service.get(new GetAppointmentQuery(OWNER, APPOINTMENT_ID));

        // Assert
        assertThat(result.id()).isEqualTo(APPOINTMENT_ID.value());
    }

    @Test
    void reschedulePersistsTheLoadedAppointment() {
        // Arrange
        Appointment appointment = appointment();
        when(repository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

        // Act
        service.reschedule(new RescheduleAppointmentCommand(OWNER, APPOINTMENT_ID, laterDate()));

        // Assert
        verify(repository).save(appointment);
    }

    @Test
    void reschedulePublishesPreviousAndNewDates() {
        // Arrange
        Appointment appointment = appointment();
        AppointmentDate previousDate = appointment.scheduledAt();
        AppointmentDate newDate = laterDate();
        when(repository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

        // Act
        service.reschedule(new RescheduleAppointmentCommand(OWNER, APPOINTMENT_ID, newDate));

        // Assert
        AppointmentRescheduledEvent event = publishedRescheduledEvent();
        assertThat(event.previousDate()).isEqualTo(previousDate);
        assertThat(event.newDate()).isEqualTo(newDate);
    }

    @Test
    void rescheduleReturnsRescheduledAppointment() {
        // Arrange
        when(repository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment()));

        // Act
        var result = service.reschedule(new RescheduleAppointmentCommand(OWNER, APPOINTMENT_ID, laterDate()));

        // Assert
        assertThat(result.status()).isEqualTo(AppointmentStatus.RESCHEDULED);
    }

    @Test
    void cancelPersistsTheLoadedAppointment() {
        // Arrange
        Appointment appointment = appointment();
        when(repository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

        // Act
        service.cancel(new CancelAppointmentCommand(OWNER, APPOINTMENT_ID, REASON));

        // Assert
        verify(repository).save(appointment);
    }

    @Test
    void cancelPublishesReasonAndPsychologistActor() {
        // Arrange
        when(repository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment()));

        // Act
        service.cancel(new CancelAppointmentCommand(OWNER, APPOINTMENT_ID, REASON));

        // Assert
        AppointmentCancelledEvent event = publishedCancelledEvent();
        assertThat(event.reason()).isEqualTo(REASON);
        assertThat(event.cancelledBy().value()).isEqualTo("psychologist");
    }

    @Test
    void cancelReturnsCancelledAppointment() {
        // Arrange
        when(repository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment()));

        // Act
        var result = service.cancel(new CancelAppointmentCommand(OWNER, APPOINTMENT_ID, REASON));

        // Assert
        assertThat(result.status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void completePersistsTheLoadedAppointment() {
        // Arrange
        Appointment appointment = appointment();
        when(repository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

        // Act
        service.complete(new CompleteAppointmentCommand(OWNER, APPOINTMENT_ID));

        // Assert
        verify(repository).save(appointment);
    }

    @Test
    void completePublishesEventWithAppointmentDetails() {
        // Arrange
        when(repository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment()));

        // Act
        service.complete(new CompleteAppointmentCommand(OWNER, APPOINTMENT_ID));

        // Assert
        AppointmentCompletedEvent event = publishedCompletedEvent();
        assertThat(event.appointmentId()).isEqualTo(APPOINTMENT_ID);
        assertThat(event.patientId()).isEqualTo(PATIENT_ID);
        assertThat(event.psychologistId()).isEqualTo(OWNER);
        assertThat(event.appointmentType()).isEqualTo(AppointmentType.IN_PERSON);
    }

    @Test
    void completeReturnsCompletedAppointment() {
        // Arrange
        when(repository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment()));

        // Act
        var result = service.complete(new CompleteAppointmentCommand(OWNER, APPOINTMENT_ID));

        // Assert
        assertThat(result.status()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void rejectsMissingAppointment() {
        // Arrange
        when(repository.findById(APPOINTMENT_ID)).thenReturn(Optional.empty());

        // Act
        Throwable thrown = catchThrowable(() -> service.get(new GetAppointmentQuery(OWNER, APPOINTMENT_ID)));

        // Assert
        assertThat(thrown).isInstanceOf(AppointmentNotFoundException.class);
    }

    @Test
    void completeRejectsAppointmentOwnedByAnotherPsychologist() {
        // Arrange
        when(repository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment()));

        // Act
        Throwable thrown = catchThrowable(() -> service.complete(new CompleteAppointmentCommand(OTHER, APPOINTMENT_ID)));

        // Assert
        assertThat(thrown).isInstanceOf(AppointmentAccessDeniedException.class);
    }

    @Test
    void foreignCompletionIsNeverSaved() {
        // Arrange
        when(repository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment()));

        // Act
        catchThrowable(() -> service.complete(new CompleteAppointmentCommand(OTHER, APPOINTMENT_ID)));

        // Assert
        verify(repository, never()).save(any());
    }

    @Test
    void foreignCompletionLeavesAppointmentScheduled() {
        // Arrange
        Appointment appointment = appointment();
        when(repository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

        // Act
        catchThrowable(() -> service.complete(new CompleteAppointmentCommand(OTHER, APPOINTMENT_ID)));

        // Assert
        assertThat(appointment.status()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    private Appointment savedAppointment() {
        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private AppointmentScheduledEvent publishedScheduledEvent() {
        ArgumentCaptor<AppointmentScheduledEvent> captor = ArgumentCaptor.forClass(AppointmentScheduledEvent.class);
        verify(publisher).publish(captor.capture());
        return captor.getValue();
    }

    private AppointmentRescheduledEvent publishedRescheduledEvent() {
        ArgumentCaptor<AppointmentRescheduledEvent> captor = ArgumentCaptor.forClass(AppointmentRescheduledEvent.class);
        verify(publisher).publish(captor.capture());
        return captor.getValue();
    }

    private AppointmentCancelledEvent publishedCancelledEvent() {
        ArgumentCaptor<AppointmentCancelledEvent> captor = ArgumentCaptor.forClass(AppointmentCancelledEvent.class);
        verify(publisher).publish(captor.capture());
        return captor.getValue();
    }

    private AppointmentCompletedEvent publishedCompletedEvent() {
        ArgumentCaptor<AppointmentCompletedEvent> captor = ArgumentCaptor.forClass(AppointmentCompletedEvent.class);
        verify(publisher).publish(captor.capture());
        return captor.getValue();
    }

    private ScheduleAppointmentCommand scheduleCommand() {
        return new ScheduleAppointmentCommand(OWNER, PATIENT_ID,
                new AppointmentDate(Instant.now().plusSeconds(3600)), AppointmentType.ONLINE);
    }

    private AppointmentDate laterDate() {
        return new AppointmentDate(Instant.now().plusSeconds(7200));
    }

    private Appointment appointment() {
        return Appointment.create(APPOINTMENT_ID, OWNER, PATIENT_ID,
                new AppointmentDate(Instant.now().plusSeconds(3600)),
                AppointmentType.IN_PERSON, CREATED_AT);
    }
}
