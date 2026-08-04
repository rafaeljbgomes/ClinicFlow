package com.clinicflow.appointment.adapters.outbound.persistence;

import com.clinicflow.appointment.domain.Appointment;
import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.AppointmentType;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
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
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JpaAppointmentRepository repository;
    @Autowired
    private SpringDataAppointmentJpaRepository delegate;

    @Test
    void persistsAndFiltersAppointmentsByOwnerAndPatient() {
        PsychologistId owner = new PsychologistId(UUID.randomUUID());
        PatientId patientId = new PatientId(UUID.randomUUID());
        Appointment expected = appointment(UUID.randomUUID(), owner, patientId);
        Appointment other = appointment(UUID.randomUUID(), new PsychologistId(UUID.randomUUID()),
                new PatientId(UUID.randomUUID()));

        repository.save(expected);
        repository.save(other);
        delegate.flush();

        assertThat(repository.findById(expected.id())).contains(expected);
        assertThat(repository.findByPsychologistId(owner)).containsExactly(expected);
        assertThat(repository.findByPatientId(patientId)).containsExactly(expected);
    }

    private Appointment appointment(UUID id, PsychologistId owner, PatientId patientId) {
        return Appointment.create(new AppointmentId(id), owner, patientId,
                new AppointmentDate(Instant.parse("2099-07-01T10:00:00Z")),
                AppointmentType.ONLINE, Instant.parse("2026-06-01T10:00:00Z"));
    }
}
