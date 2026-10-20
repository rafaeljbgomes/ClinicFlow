package com.clinicflow.appointment.adapters.outbound.persistence;

import com.clinicflow.appointment.domain.Appointment;
import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.AppointmentType;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAppointmentRepository.class, AppointmentPersistenceMapperImpl.class})
@Testcontainers
class JpaAppointmentRepositoryIT {
    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired
    private JpaAppointmentRepository repository;
    @Autowired
    private SpringDataAppointmentJpaRepository delegate;

    @Test
    void findByIdReturnsPersistedAppointment() {
        // Arrange
        Appointment expected = appointment(new PsychologistId(UUID.randomUUID()), new PatientId(UUID.randomUUID()));
        persist(expected, unrelatedAppointment());

        // Act
        Optional<Appointment> found = repository.findById(expected.id());

        // Assert
        assertThat(found).contains(expected);
    }

    @Test
    void findByPsychologistIdReturnsOnlyOwnedAppointments() {
        // Arrange
        PsychologistId owner = new PsychologistId(UUID.randomUUID());
        Appointment expected = appointment(owner, new PatientId(UUID.randomUUID()));
        persist(expected, unrelatedAppointment());

        // Act
        List<Appointment> found = repository.findByPsychologistId(owner);

        // Assert
        assertThat(found).containsExactly(expected);
    }

    @Test
    void findByPatientIdReturnsOnlyThatPatientsAppointments() {
        // Arrange
        PatientId patientId = new PatientId(UUID.randomUUID());
        Appointment expected = appointment(new PsychologistId(UUID.randomUUID()), patientId);
        persist(expected, unrelatedAppointment());

        // Act
        List<Appointment> found = repository.findByPatientId(patientId);

        // Assert
        assertThat(found).containsExactly(expected);
    }

    private void persist(Appointment... appointments) {
        for (Appointment appointment : appointments) {
            repository.save(appointment);
        }
        delegate.flush();
    }

    private Appointment unrelatedAppointment() {
        return appointment(new PsychologistId(UUID.randomUUID()), new PatientId(UUID.randomUUID()));
    }

    private Appointment appointment(PsychologistId owner, PatientId patientId) {
        return Appointment.create(new AppointmentId(UUID.randomUUID()), owner, patientId,
                new AppointmentDate(Instant.parse("2099-07-01T10:00:00Z")),
                AppointmentType.ONLINE, Instant.parse("2026-06-01T10:00:00Z"));
    }
}
