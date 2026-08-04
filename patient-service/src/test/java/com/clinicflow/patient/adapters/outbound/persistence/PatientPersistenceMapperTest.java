package com.clinicflow.patient.adapters.outbound.persistence;

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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PatientPersistenceMapperTest {
    private final PatientPersistenceMapper mapper = Mappers.getMapper(PatientPersistenceMapper.class);

    @Test
    void roundTripsDomainAndPersistenceModels() {
        Patient patient = patient();

        Patient restored = mapper.toDomain(mapper.toJpa(patient));

        assertThat(restored).isEqualTo(patient);
        assertThat(restored.phone()).contains(new PhoneNumber("+351912345678"));
        assertThat(restored.preferredName()).contains(new PreferredName("Pat"));
        assertThat(restored.emergencyContact()).contains(new EmergencyContact(
                "Emergency One", new PhoneNumber("+351923456789"), "Partner"));
    }

    @Test
    void repositorySaveKeepsTheSuppliedDomainInstance() {
        SpringDataPatientJpaRepository delegate = mock(SpringDataPatientJpaRepository.class);
        PatientPersistenceMapper mockedMapper = mock(PatientPersistenceMapper.class);
        Patient patient = patient();
        JpaPatientEntity jpa = mapper.toJpa(patient);
        when(mockedMapper.toJpa(patient)).thenReturn(jpa);

        new JpaPatientRepository(delegate, mockedMapper).save(patient);

        verify(delegate).save(jpa);
        assertThat(patient.fullName()).isEqualTo(new FullName("Patient Name"));
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
