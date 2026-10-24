package com.clinicflow.appointment.adapters.outbound.persistence;

import com.clinicflow.appointment.application.exceptions.StaleResourceVersionException;
import com.clinicflow.appointment.domain.Appointment;
import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.AppointmentType;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentPersistenceMapperTest {
    private final AppointmentPersistenceMapper mapper = Mappers.getMapper(AppointmentPersistenceMapper.class);

    @Test
    void roundTripsDomainAndPersistenceModels() {
        // Arrange
        Appointment appointment = appointment();

        // Act
        Appointment restored = mapper.toDomain(mapper.toJpa(appointment));

        // Assert
        assertThat(restored).isEqualTo(appointment);
        assertThat(restored.scheduledAt()).isEqualTo(appointment.scheduledAt());
    }

    @Test
    void roundTripsTheVersion() {
        // Arrange
        Appointment appointment = storedAppointment(6L);

        // Act
        Appointment restored = mapper.toDomain(mapper.toJpa(appointment));

        // Assert
        assertThat(restored.version()).contains(6L);
    }

    @Test
    void repositorySaveFlushesTheMappedEntity() {
        // Arrange
        SpringDataAppointmentJpaRepository delegate = mock(SpringDataAppointmentJpaRepository.class);
        AppointmentPersistenceMapper mockedMapper = mock(AppointmentPersistenceMapper.class);
        Appointment appointment = appointment();
        JpaAppointmentEntity jpa = mapper.toJpa(appointment);
        when(mockedMapper.toJpa(appointment)).thenReturn(jpa);
        JpaAppointmentRepository repository = new JpaAppointmentRepository(delegate, mockedMapper);

        // Act
        repository.save(appointment);

        // Assert
        verify(delegate).saveAndFlush(jpa);
    }

    @Test
    void repositorySaveReturnsTheStoredAppointment() {
        // Arrange
        SpringDataAppointmentJpaRepository delegate = mock(SpringDataAppointmentJpaRepository.class);
        when(delegate.saveAndFlush(any())).thenReturn(mapper.toJpa(storedAppointment(0L)));
        JpaAppointmentRepository repository = new JpaAppointmentRepository(delegate, mapper);

        // Act
        Appointment saved = repository.save(appointment());

        // Assert
        assertThat(saved.version()).contains(0L);
    }

    @Test
    void repositorySaveReportsAConcurrentWriteAsAStaleVersion() {
        // Arrange
        SpringDataAppointmentJpaRepository delegate = mock(SpringDataAppointmentJpaRepository.class);
        when(delegate.saveAndFlush(any())).thenThrow(new ObjectOptimisticLockingFailureException(
                JpaAppointmentEntity.class, UUID.randomUUID()));
        JpaAppointmentRepository repository = new JpaAppointmentRepository(delegate, mapper);

        // Act
        Throwable thrown = catchThrowable(() -> repository.save(storedAppointment(1L)));

        // Assert
        assertThat(thrown).isInstanceOf(StaleResourceVersionException.class);
    }

    private Appointment storedAppointment(Long version) {
        Appointment appointment = appointment();
        return Appointment.rehydrate(appointment.id(), appointment.psychologistId(), appointment.patientId(),
                appointment.scheduledAt(), appointment.status(), appointment.type(), null,
                appointment.createdAt(), appointment.updatedAt(), version);
    }

    private Appointment appointment() {
        Instant now = Instant.parse("2026-05-26T10:00:00Z");
        return Appointment.create(new AppointmentId(UUID.randomUUID()), new PsychologistId(UUID.randomUUID()),
                new PatientId(UUID.randomUUID()), new AppointmentDate(now.plusSeconds(3600)),
                AppointmentType.ONLINE, now);
    }
}
