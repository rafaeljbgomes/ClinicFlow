package com.clinicflow.appointment.adapters.outbound.persistence;

import com.clinicflow.appointment.application.queries.ListAppointmentsQuery;
import com.clinicflow.appointment.application.queries.PageQuery;
import com.clinicflow.appointment.application.queries.ScheduleOrder;
import com.clinicflow.appointment.application.results.PageResult;
import com.clinicflow.appointment.domain.AppointmentStatus;
import com.clinicflow.appointment.domain.CancellationReason;
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
import java.util.Set;
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
    void findPageReturnsOnlyOwnedAppointmentsInScheduleOrder() {
        // Arrange
        PsychologistId owner = new PsychologistId(UUID.randomUUID());
        Appointment later = appointment(owner, at(2));
        Appointment earlier = appointment(owner, at(1));
        persist(later, earlier, unrelatedAppointment());

        // Act
        PageResult<Appointment> found = repository.findPage(query(owner, null, null, Set.of(), 0, 20));

        // Assert
        assertThat(found.items()).containsExactly(earlier, later);
        assertThat(found.totalElements()).isEqualTo(2);
    }

    @Test
    void findPageAppliesTheHalfOpenWindowAndStatusFilter() {
        // Arrange
        PsychologistId owner = new PsychologistId(UUID.randomUUID());
        Appointment before = appointment(owner, at(0));
        Appointment atStart = appointment(owner, at(1));
        Appointment cancelled = appointment(owner, at(2))
                .cancel(new CancellationReason("Patient asked to cancel"), Instant.parse("2026-06-02T10:00:00Z"));
        Appointment atEnd = appointment(owner, at(3));
        persist(before, atStart, cancelled, atEnd);

        // Act
        PageResult<Appointment> window = repository.findPage(query(owner, at(1), at(3), Set.of(), 0, 20));
        PageResult<Appointment> active = repository.findPage(query(owner, at(1), at(3),
                Set.of(AppointmentStatus.SCHEDULED, AppointmentStatus.RESCHEDULED), 0, 20));

        // Assert
        assertThat(window.items()).extracting(Appointment::id).containsExactly(atStart.id(), cancelled.id());
        assertThat(active.items()).extracting(Appointment::id).containsExactly(atStart.id());
    }

    @Test
    void findPageSplitsResultsAcrossPagesWithoutOverlap() {
        // Arrange
        PsychologistId owner = new PsychologistId(UUID.randomUUID());
        Appointment first = appointment(owner, at(1));
        Appointment second = appointment(owner, at(2));
        Appointment third = appointment(owner, at(3));
        persist(third, first, second);

        // Act
        PageResult<Appointment> page0 = repository.findPage(query(owner, null, null, Set.of(), 0, 2));
        PageResult<Appointment> page1 = repository.findPage(query(owner, null, null, Set.of(), 1, 2));

        // Assert
        assertThat(page0.items()).containsExactly(first, second);
        assertThat(page1.items()).containsExactly(third);
        assertThat(page0.totalPages()).isEqualTo(2);
    }

    @Test
    void findPageListsLatestFirstWhenAskedForDescendingOrder() {
        // Arrange
        PsychologistId owner = new PsychologistId(UUID.randomUUID());
        Appointment earlier = appointment(owner, at(1));
        Appointment later = appointment(owner, at(2));
        persist(earlier, later);

        // Act
        PageResult<Appointment> found = repository.findPage(new ListAppointmentsQuery(owner, null, null, Set.of(),
                ScheduleOrder.DESC, new PageQuery(0, 20)));

        // Assert
        assertThat(found.items()).containsExactly(later, earlier);
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

    private Appointment appointment(PsychologistId owner, Instant scheduledAt) {
        return Appointment.create(new AppointmentId(UUID.randomUUID()), owner, new PatientId(UUID.randomUUID()),
                new AppointmentDate(scheduledAt), AppointmentType.ONLINE, Instant.parse("2026-06-01T10:00:00Z"));
    }

    private static Instant at(int days) {
        return Instant.parse("2099-07-01T10:00:00Z").plusSeconds(86_400L * days);
    }

    private static ListAppointmentsQuery query(PsychologistId owner, Instant from, Instant to,
                                               Set<AppointmentStatus> statuses, int page, int size) {
        return new ListAppointmentsQuery(owner, from, to, statuses, ScheduleOrder.ASC, new PageQuery(page, size));
    }

    private Appointment appointment(PsychologistId owner, PatientId patientId) {
        return Appointment.create(new AppointmentId(UUID.randomUUID()), owner, patientId,
                new AppointmentDate(Instant.parse("2099-07-01T10:00:00Z")),
                AppointmentType.ONLINE, Instant.parse("2026-06-01T10:00:00Z"));
    }
}
