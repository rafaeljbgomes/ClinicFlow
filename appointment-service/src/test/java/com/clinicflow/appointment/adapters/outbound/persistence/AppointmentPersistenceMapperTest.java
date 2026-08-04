package com.clinicflow.appointment.adapters.outbound.persistence;

import com.clinicflow.appointment.domain.Appointment;
import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.AppointmentType;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppointmentPersistenceMapperTest {
    private final AppointmentPersistenceMapper mapper = Mappers.getMapper(AppointmentPersistenceMapper.class);

    @Test
    void roundTripsDomainAndPersistenceModels() {
        Appointment appointment = appointment();

        Appointment restored = mapper.toDomain(mapper.toJpa(appointment));

        assertThat(restored).isEqualTo(appointment);
        assertThat(restored.scheduledAt()).isEqualTo(appointment.scheduledAt());
    }

    @Test
    void repositorySaveKeepsTheSuppliedDomainInstance() {
        SpringDataAppointmentJpaRepository delegate = mock(SpringDataAppointmentJpaRepository.class);
        AppointmentPersistenceMapper mockedMapper = mock(AppointmentPersistenceMapper.class);
        Appointment appointment = appointment();
        JpaAppointmentEntity jpa = mapper.toJpa(appointment);
        when(mockedMapper.toJpa(appointment)).thenReturn(jpa);

        new JpaAppointmentRepository(delegate, mockedMapper).save(appointment);

        verify(delegate).save(jpa);
        assertThat(appointment.type()).isEqualTo(AppointmentType.ONLINE);
    }

    private Appointment appointment() {
        Instant now = Instant.parse("2026-05-26T10:00:00Z");
        return Appointment.create(new AppointmentId(UUID.randomUUID()), new PsychologistId(UUID.randomUUID()),
                new PatientId(UUID.randomUUID()), new AppointmentDate(now.plusSeconds(3600)),
                AppointmentType.ONLINE, now);
    }
}
