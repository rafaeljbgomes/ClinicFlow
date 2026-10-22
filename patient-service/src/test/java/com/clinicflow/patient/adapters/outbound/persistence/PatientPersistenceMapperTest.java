package com.clinicflow.patient.adapters.outbound.persistence;

import com.clinicflow.patient.application.exceptions.StaleResourceVersionException;
import com.clinicflow.patient.domain.BirthDate;
import com.clinicflow.patient.domain.ConsentStatus;
import com.clinicflow.patient.domain.ContactPreference;
import com.clinicflow.patient.domain.EmailAddress;
import com.clinicflow.patient.domain.EmergencyContact;
import com.clinicflow.patient.domain.FullName;
import com.clinicflow.patient.domain.Patient;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PhoneNumber;
import com.clinicflow.patient.domain.PreferredName;
import com.clinicflow.patient.domain.PsychologistId;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PatientPersistenceMapperTest {
    private final PatientPersistenceMapper mapper = Mappers.getMapper(PatientPersistenceMapper.class);

    @Test
    void roundTripsDomainAndPersistenceModels() {
        // Arrange
        Patient patient = patient();

        // Act
        Patient restored = mapper.toDomain(mapper.toJpa(patient));

        // Assert
        assertThat(restored).isEqualTo(patient);
        assertThat(restored.phone()).contains(new PhoneNumber("+351912345678"));
        assertThat(restored.preferredName()).contains(new PreferredName("Pat"));
        assertThat(restored.emergencyContact()).contains(new EmergencyContact(
                "Emergency One", new PhoneNumber("+351923456789"), "Partner"));
    }

    @Test
    void roundTripsTheVersion() {
        // Arrange
        Patient patient = storedPatient(4L);

        // Act
        Patient restored = mapper.toDomain(mapper.toJpa(patient));

        // Assert
        assertThat(restored.version()).contains(4L);
    }

    @Test
    void repositorySaveFlushesTheMappedEntity() {
        // Arrange
        SpringDataPatientJpaRepository delegate = mock(SpringDataPatientJpaRepository.class);
        PatientPersistenceMapper mockedMapper = mock(PatientPersistenceMapper.class);
        Patient patient = patient();
        JpaPatientEntity jpa = mapper.toJpa(patient);
        when(mockedMapper.toJpa(patient)).thenReturn(jpa);
        JpaPatientRepository repository = new JpaPatientRepository(delegate, mockedMapper);

        // Act
        repository.save(patient);

        // Assert
        verify(delegate).saveAndFlush(jpa);
    }

    @Test
    void repositorySaveReturnsTheStoredPatient() {
        // Arrange
        SpringDataPatientJpaRepository delegate = mock(SpringDataPatientJpaRepository.class);
        when(delegate.saveAndFlush(any())).thenReturn(mapper.toJpa(storedPatient(0L)));
        JpaPatientRepository repository = new JpaPatientRepository(delegate, mapper);

        // Act
        Patient saved = repository.save(patient());

        // Assert
        assertThat(saved.version()).contains(0L);
    }

    @Test
    void repositorySaveReportsAConcurrentWriteAsAStaleVersion() {
        // Arrange
        SpringDataPatientJpaRepository delegate = mock(SpringDataPatientJpaRepository.class);
        when(delegate.saveAndFlush(any())).thenThrow(new ObjectOptimisticLockingFailureException(
                JpaPatientEntity.class, UUID.randomUUID()));
        JpaPatientRepository repository = new JpaPatientRepository(delegate, mapper);

        // Act
        Throwable thrown = catchThrowable(() -> repository.save(storedPatient(1L)));

        // Assert
        assertThat(thrown).isInstanceOf(StaleResourceVersionException.class);
    }

    private Patient storedPatient(Long version) {
        Patient patient = patient();
        return Patient.rehydrate(patient.id(), patient.psychologistId(), patient.email(), patient.fullName(),
                patient.preferredName().orElseThrow(), patient.birthDate().orElseThrow(),
                patient.phone().orElseThrow(), patient.emergencyContact().orElseThrow(),
                patient.contactPreference(), patient.consentStatus(), patient.consentUpdatedAt(),
                patient.status(), patient.createdAt(), patient.updatedAt(), version);
    }

    private Patient patient() {
        Instant now = Instant.parse("2026-05-26T10:00:00Z");
        return Patient.create(new PatientId(UUID.randomUUID()), new PsychologistId(UUID.randomUUID()),
                new EmailAddress("patient@example.com"), new FullName("Patient Name"),
                new PreferredName("Pat"), new BirthDate(LocalDate.of(1990, 1, 1)),
                new PhoneNumber("+351912345678"),
                new EmergencyContact("Emergency One", new PhoneNumber("+351923456789"), "Partner"),
                ContactPreference.EMAIL, ConsentStatus.GRANTED, now);
    }
}
