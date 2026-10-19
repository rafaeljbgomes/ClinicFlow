package com.clinicflow.patient.adapters.outbound.persistence;

import com.clinicflow.patient.domain.ConsentStatus;
import com.clinicflow.patient.domain.ContactPreference;
import com.clinicflow.patient.domain.EmailAddress;
import com.clinicflow.patient.domain.FullName;
import com.clinicflow.patient.domain.Patient;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PhoneNumber;
import com.clinicflow.patient.domain.PsychologistId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaPatientRepository.class, PatientPersistenceMapperImpl.class})
@Testcontainers
class JpaPatientRepositoryIT {
    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired
    private JpaPatientRepository repository;
    @Autowired
    private SpringDataPatientJpaRepository delegate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findByIdReturnsPersistedPatient() {
        // Arrange
        Patient patient = patient(UUID.randomUUID(), new PsychologistId(UUID.randomUUID()), "owned@example.com");
        persist(patient);

        // Act
        Optional<Patient> found = repository.findById(patient.id());

        // Assert
        assertThat(found).contains(patient);
    }

    @Test
    void findByPsychologistIdReturnsOnlyOwnedPatients() {
        // Arrange
        PsychologistId owner = new PsychologistId(UUID.randomUUID());
        Patient owned = patient(UUID.randomUUID(), owner, "owned@example.com");
        Patient other = patient(UUID.randomUUID(), new PsychologistId(UUID.randomUUID()), "other@example.com");
        persist(owned, other);

        // Act
        List<Patient> found = repository.findByPsychologistId(owner);

        // Assert
        assertThat(found).containsExactly(owned);
    }

    @Test
    void flywayConstraintRejectsNonE164Phone() {
        // Arrange
        String insert = """
                INSERT INTO patients (
                    id, psychologist_id, email, full_name, phone,
                    contact_preference, consent_status, consent_updated_at,
                    status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-06-01T10:00:00Z");

        // Act
        Throwable thrown = catchThrowable(() -> jdbcTemplate.update(insert,
                UUID.randomUUID(), UUID.randomUUID(), "patient@example.com", "Patient Name",
                "912345678", "EMAIL", "GRANTED", timestamp, "ACTIVE", timestamp, timestamp));

        // Assert
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void persist(Patient... patients) {
        for (Patient patient : patients) {
            repository.save(patient);
        }
        delegate.flush();
    }

    private Patient patient(UUID id, PsychologistId owner, String email) {
        return Patient.create(new PatientId(id), owner, new EmailAddress(email),
                new FullName("Patient Name"), null, null, new PhoneNumber("+351912345678"),
                null, ContactPreference.EMAIL, ConsentStatus.GRANTED,
                Instant.parse("2026-06-01T10:00:00Z"));
    }
}
