package com.clinicflow.appointment.infrastructure.bootstrap;

import com.clinicflow.appointment.application.ports.AppointmentRepository;
import com.clinicflow.appointment.domain.Appointment;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.AppointmentStatus;
import com.clinicflow.appointment.domain.AppointmentType;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DemoDataBootstrapTest {
    @Test
    void canBeConstructedBySpring() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            // Arrange
            context.getEnvironment().getPropertySources().addFirst(demoBootstrapProperty());
            context.registerBean(AppointmentRepository.class, InMemoryAppointmentRepository::new);
            context.registerBean(DemoDataBootstrap.class);

            // Act
            context.refresh();

            // Assert
            assertThat(context.getBean(DemoDataBootstrap.class)).isNotNull();
        }
    }

    @Test
    void seedsAppointmentsInEveryLifecycleStatus() throws Exception {
        // Arrange
        InMemoryAppointmentRepository appointments = new InMemoryAppointmentRepository();
        DemoDataBootstrap bootstrap = bootstrap(appointments);

        // Act
        bootstrap.run(null);

        // Assert
        assertThat(appointments.records().values()).extracting(Appointment::status)
                .contains(AppointmentStatus.SCHEDULED, AppointmentStatus.RESCHEDULED,
                        AppointmentStatus.CANCELLED, AppointmentStatus.COMPLETED);
    }

    @Test
    void seedsAppointmentsOfEveryType() throws Exception {
        // Arrange
        InMemoryAppointmentRepository appointments = new InMemoryAppointmentRepository();
        DemoDataBootstrap bootstrap = bootstrap(appointments);

        // Act
        bootstrap.run(null);

        // Assert
        assertThat(appointments.records().values()).extracting(Appointment::type)
                .contains(AppointmentType.ONLINE, AppointmentType.IN_PERSON);
    }

    @Test
    void repeatedRunDoesNotDuplicateDemoAppointments() throws Exception {
        // Arrange
        InMemoryAppointmentRepository appointments = new InMemoryAppointmentRepository();
        DemoDataBootstrap bootstrap = bootstrap(appointments);
        bootstrap.run(null);

        // Act
        bootstrap.run(null);

        // Assert
        assertThat(appointments.records()).hasSize(6);
    }

    private static DemoDataBootstrap bootstrap(InMemoryAppointmentRepository appointments) {
        return new DemoDataBootstrap(
                appointments,
                Clock.fixed(Instant.parse("2026-07-05T10:00:00Z"), ZoneOffset.UTC)
        );
    }

    private static MapPropertySource demoBootstrapProperty() {
        return new MapPropertySource("test", Map.of("clinicflow.demo.bootstrap.enabled", "true"));
    }

    private static final class InMemoryAppointmentRepository implements AppointmentRepository {
        private final Map<AppointmentId, Appointment> records = new LinkedHashMap<>();

        @Override
        public void save(Appointment appointment) {
            records.put(appointment.id(), appointment);
        }

        @Override
        public Optional<Appointment> findById(AppointmentId id) {
            return Optional.ofNullable(records.get(id));
        }

        @Override
        public List<Appointment> findByPsychologistId(PsychologistId psychologistId) {
            return records.values().stream()
                    .filter(appointment -> appointment.psychologistId().equals(psychologistId))
                    .toList();
        }

        @Override
        public List<Appointment> findByPatientId(PatientId patientId) {
            return records.values().stream()
                    .filter(appointment -> appointment.patientId().equals(patientId))
                    .toList();
        }

        Map<AppointmentId, Appointment> records() {
            return records;
        }
    }
}
