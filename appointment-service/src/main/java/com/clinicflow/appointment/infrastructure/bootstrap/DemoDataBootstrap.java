package com.clinicflow.appointment.infrastructure.bootstrap;

import com.clinicflow.appointment.application.ports.AppointmentRepository;
import com.clinicflow.appointment.domain.Appointment;
import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.AppointmentStatus;
import com.clinicflow.appointment.domain.AppointmentType;
import com.clinicflow.appointment.domain.CancellationReason;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "clinicflow.demo.bootstrap", name = "enabled", havingValue = "true")
public class DemoDataBootstrap implements ApplicationRunner {
    private static final UUID PSYCHOLOGIST_SOFIA = uuid("00000000-0000-4000-8000-000000000011");
    private static final UUID PSYCHOLOGIST_MIGUEL = uuid("00000000-0000-4000-8000-000000000012");

    private final AppointmentRepository appointments;
    private final Clock clock;

    @Autowired
    public DemoDataBootstrap(AppointmentRepository appointments) {
        this(appointments, Clock.systemUTC());
    }

    DemoDataBootstrap(AppointmentRepository appointments, Clock clock) {
        this.appointments = appointments;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        for (DemoAppointment appointment : demoAppointments(now)) {
            appointments.save(Appointment.rehydrate(
                    new AppointmentId(appointment.id()),
                    new PsychologistId(appointment.psychologistId()),
                    new PatientId(appointment.patientId()),
                    new AppointmentDate(appointment.scheduledAt()),
                    appointment.status(),
                    appointment.type(),
                    appointment.cancellationReason() == null ? null : new CancellationReason(appointment.cancellationReason()),
                    now.minus(30, ChronoUnit.DAYS),
                    now
            ));
        }
    }

    private static List<DemoAppointment> demoAppointments(Instant now) {
        return List.of(
                new DemoAppointment(uuid("20000000-0000-4000-8000-000000000001"), PSYCHOLOGIST_SOFIA,
                        patient(1), now.plus(2, ChronoUnit.DAYS), AppointmentStatus.SCHEDULED,
                        AppointmentType.ONLINE, null),
                new DemoAppointment(uuid("20000000-0000-4000-8000-000000000002"), PSYCHOLOGIST_SOFIA,
                        patient(2), now.plus(8, ChronoUnit.DAYS), AppointmentStatus.RESCHEDULED,
                        AppointmentType.IN_PERSON, null),
                new DemoAppointment(uuid("20000000-0000-4000-8000-000000000003"), PSYCHOLOGIST_SOFIA,
                        patient(3), now.minus(3, ChronoUnit.DAYS), AppointmentStatus.COMPLETED,
                        AppointmentType.ONLINE, null),
                new DemoAppointment(uuid("20000000-0000-4000-8000-000000000004"), PSYCHOLOGIST_MIGUEL,
                        patient(4), now.plus(1, ChronoUnit.DAYS), AppointmentStatus.SCHEDULED,
                        AppointmentType.IN_PERSON, null),
                new DemoAppointment(uuid("20000000-0000-4000-8000-000000000005"), PSYCHOLOGIST_MIGUEL,
                        patient(5), now.minus(5, ChronoUnit.DAYS), AppointmentStatus.CANCELLED,
                        AppointmentType.ONLINE, "Paciente pediu cancelamento por indisponibilidade"),
                new DemoAppointment(uuid("20000000-0000-4000-8000-000000000006"), PSYCHOLOGIST_MIGUEL,
                        patient(6), now.minus(1, ChronoUnit.DAYS), AppointmentStatus.COMPLETED,
                        AppointmentType.IN_PERSON, null)
        );
    }

    private static UUID patient(int suffix) {
        return uuid("10000000-0000-4000-8000-00000000000" + suffix);
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

    private record DemoAppointment(UUID id, UUID psychologistId, UUID patientId, Instant scheduledAt,
                                   AppointmentStatus status, AppointmentType type, String cancellationReason) {
    }
}
