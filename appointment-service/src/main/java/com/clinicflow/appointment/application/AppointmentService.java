package com.clinicflow.appointment.application;

import com.clinicflow.appointment.application.commands.CancelAppointmentCommand;
import com.clinicflow.appointment.application.commands.CompleteAppointmentCommand;
import com.clinicflow.appointment.application.commands.RescheduleAppointmentCommand;
import com.clinicflow.appointment.application.commands.ScheduleAppointmentCommand;
import com.clinicflow.appointment.application.exceptions.AppointmentNotFoundException;
import com.clinicflow.appointment.application.exceptions.PatientNotFoundException;
import com.clinicflow.appointment.application.exceptions.StaleResourceVersionException;
import com.clinicflow.appointment.application.mapping.AppointmentApplicationMapper;
import com.clinicflow.appointment.application.ports.AppointmentEventPublisher;
import com.clinicflow.appointment.application.ports.AppointmentRepository;
import com.clinicflow.appointment.application.ports.PatientDirectory;
import com.clinicflow.appointment.application.queries.GetAppointmentQuery;
import com.clinicflow.appointment.application.queries.ListAppointmentsQuery;
import com.clinicflow.appointment.application.results.AppointmentResult;
import com.clinicflow.appointment.application.results.PageResult;
import com.clinicflow.appointment.domain.Appointment;
import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.CancellationActor;
import com.clinicflow.appointment.domain.PsychologistId;
import com.clinicflow.appointment.domain.events.AppointmentCancelledEvent;
import com.clinicflow.appointment.domain.events.AppointmentCompletedEvent;
import com.clinicflow.appointment.domain.events.AppointmentRescheduledEvent;
import com.clinicflow.appointment.domain.events.AppointmentScheduledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AppointmentService {
    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);
    private final AppointmentRepository appointmentRepository;
    private final AppointmentEventPublisher eventPublisher;
    private final PatientDirectory patientDirectory;
    private final AppointmentApplicationMapper mapper;

    public AppointmentService(AppointmentRepository appointmentRepository, AppointmentEventPublisher eventPublisher,
                              PatientDirectory patientDirectory, AppointmentApplicationMapper mapper) {
        this.appointmentRepository = appointmentRepository;
        this.eventPublisher = eventPublisher;
        this.patientDirectory = patientDirectory;
        this.mapper = mapper;
    }

    @Transactional
    public AppointmentResult schedule(ScheduleAppointmentCommand command) {
        // Unknown and foreign patients get the same answer so the endpoint is not an existence oracle.
        if (!patientDirectory.isOwnedBy(command.patientId(), command.psychologistId())) {
            throw new PatientNotFoundException();
        }
        Instant now = Instant.now();
        Appointment appointment = Appointment.create(new AppointmentId(UUID.randomUUID()), command.psychologistId(),
                command.patientId(), command.scheduledAt(), command.type(), now);
        Appointment saved = appointmentRepository.save(appointment);
        eventPublisher.publish(AppointmentScheduledEvent.of(saved.id(), saved.patientId(),
                saved.psychologistId(), saved.scheduledAt()));
        return mapper.toResult(saved);
    }

    @Transactional(readOnly = true)
    public PageResult<AppointmentResult> listForPsychologist(ListAppointmentsQuery query) {
        return appointmentRepository.findPage(query).map(mapper::toResult);
    }

    @Transactional(readOnly = true)
    public AppointmentResult get(GetAppointmentQuery query) {
        return mapper.toResult(requireOwnedAppointment(query.psychologistId(), query.appointmentId()));
    }

    @Transactional
    public AppointmentResult reschedule(RescheduleAppointmentCommand command) {
        Appointment appointment = requireOwnedAppointment(command.psychologistId(), command.appointmentId());
        requireVersion(appointment, command.expectedVersion());
        AppointmentDate previousDate = appointment.scheduledAt();
        appointment.reschedule(command.newDate(), Instant.now());
        // Saved before the event is recorded, so a lost race never leaves an event for a rejected change.
        Appointment saved = appointmentRepository.save(appointment);
        eventPublisher.publish(AppointmentRescheduledEvent.of(saved.id(), saved.patientId(),
                saved.psychologistId(), previousDate, saved.scheduledAt()));
        return mapper.toResult(saved);
    }

    @Transactional
    public AppointmentResult cancel(CancelAppointmentCommand command) {
        Appointment appointment = requireOwnedAppointment(command.psychologistId(), command.appointmentId());
        requireVersion(appointment, command.expectedVersion());
        appointment.cancel(command.reason(), Instant.now());
        Appointment saved = appointmentRepository.save(appointment);
        eventPublisher.publish(AppointmentCancelledEvent.of(saved.id(), saved.patientId(),
                saved.psychologistId(), new CancellationActor("psychologist"), command.reason()));
        return mapper.toResult(saved);
    }

    @Transactional
    public AppointmentResult complete(CompleteAppointmentCommand command) {
        Appointment appointment = requireOwnedAppointment(command.psychologistId(), command.appointmentId());
        requireVersion(appointment, command.expectedVersion());
        appointment.complete(Instant.now());
        Appointment saved = appointmentRepository.save(appointment);
        eventPublisher.publish(AppointmentCompletedEvent.of(saved.id(), saved.patientId(),
                saved.psychologistId(), saved.scheduledAt(), saved.type()));
        return mapper.toResult(saved);
    }

    // Another psychologist's appointment answers exactly like a missing one, so ids cannot be probed.
    private Appointment requireOwnedAppointment(PsychologistId psychologistId, AppointmentId appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(AppointmentNotFoundException::new);
        if (!appointment.psychologistId().equals(psychologistId)) {
            log.warn("appointment_access_denied appointmentId={} psychologistId={}", appointmentId.value(),
                    psychologistId.value());
            throw new AppointmentNotFoundException();
        }
        return appointment;
    }

    // Runs after the ownership check, so another psychologist's appointment still looks missing, never stale.
    private static void requireVersion(Appointment appointment, long expectedVersion) {
        if (!appointment.isAtVersion(expectedVersion)) {
            throw new StaleResourceVersionException();
        }
    }
}
