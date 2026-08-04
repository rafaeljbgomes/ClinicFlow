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
import com.clinicflow.appointment.application.results.AppointmentResult;
import com.clinicflow.appointment.domain.Appointment;
import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.CancellationActor;
import com.clinicflow.appointment.domain.PsychologistId;
import com.clinicflow.appointment.domain.events.AppointmentCancelledEvent;
import com.clinicflow.appointment.domain.events.AppointmentCompletedEvent;
import com.clinicflow.appointment.domain.events.AppointmentRescheduledEvent;
import com.clinicflow.appointment.domain.events.AppointmentScheduledEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AppointmentService {
    private final AppointmentRepository appointmentRepository;
    private final AppointmentEventPublisher eventPublisher;
    private final AppointmentApplicationMapper mapper;

    public AppointmentService(AppointmentRepository appointmentRepository, AppointmentEventPublisher eventPublisher,
                              AppointmentApplicationMapper mapper) {
        this.appointmentRepository = appointmentRepository;
        this.eventPublisher = eventPublisher;
        this.mapper = mapper;
    }

    @Transactional
    public AppointmentResult schedule(ScheduleAppointmentCommand command) {
        Instant now = Instant.now();
        Appointment appointment = Appointment.create(new AppointmentId(UUID.randomUUID()), command.psychologistId(),
                command.patientId(), command.scheduledAt(), command.type(), now);
        appointmentRepository.save(appointment);
        eventPublisher.publish(AppointmentScheduledEvent.of(appointment.id(), appointment.patientId(),
                appointment.psychologistId(), appointment.scheduledAt()));
        return mapper.toResult(appointment);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResult> listForPsychologist(ListAppointmentsQuery query) {
        return appointmentRepository.findByPsychologistId(query.psychologistId()).stream()
                .map(mapper::toResult).toList();
    }

    @Transactional(readOnly = true)
    public AppointmentResult get(GetAppointmentQuery query) {
        return mapper.toResult(requireOwnedAppointment(query.psychologistId(), query.appointmentId()));
    }

    @Transactional
    public AppointmentResult reschedule(RescheduleAppointmentCommand command) {
        Appointment appointment = requireOwnedAppointment(command.psychologistId(), command.appointmentId());
        AppointmentDate previousDate = appointment.scheduledAt();
        appointment.reschedule(command.newDate(), Instant.now());
        appointmentRepository.save(appointment);
        eventPublisher.publish(AppointmentRescheduledEvent.of(appointment.id(), appointment.patientId(),
                appointment.psychologistId(), previousDate, appointment.scheduledAt()));
        return mapper.toResult(appointment);
    }

    @Transactional
    public AppointmentResult cancel(CancelAppointmentCommand command) {
        Appointment appointment = requireOwnedAppointment(command.psychologistId(), command.appointmentId());
        appointment.cancel(command.reason(), Instant.now());
        appointmentRepository.save(appointment);
        eventPublisher.publish(AppointmentCancelledEvent.of(appointment.id(), appointment.patientId(),
                appointment.psychologistId(), new CancellationActor("psychologist"), command.reason()));
        return mapper.toResult(appointment);
    }

    @Transactional
    public AppointmentResult complete(CompleteAppointmentCommand command) {
        Appointment appointment = requireOwnedAppointment(command.psychologistId(), command.appointmentId());
        appointment.complete(Instant.now());
        appointmentRepository.save(appointment);
        eventPublisher.publish(AppointmentCompletedEvent.of(appointment.id(), appointment.patientId(),
                appointment.psychologistId(), appointment.scheduledAt(), appointment.type()));
        return mapper.toResult(appointment);
    }

    private Appointment requireOwnedAppointment(PsychologistId psychologistId, AppointmentId appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(AppointmentNotFoundException::new);
        if (!appointment.psychologistId().equals(psychologistId)) {
            throw new AppointmentAccessDeniedException();
        }
        return appointment;
    }
}
