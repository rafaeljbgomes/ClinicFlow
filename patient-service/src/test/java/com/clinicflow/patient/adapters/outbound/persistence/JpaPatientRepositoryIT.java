package com.clinicflow.patient.adapters.outbound.persistence;

import com.clinicflow.patient.application.exceptions.StaleResourceVersionException;
import com.clinicflow.patient.application.queries.ListPatientsQuery;
import com.clinicflow.patient.application.queries.PageQuery;
import com.clinicflow.patient.application.results.PageResult;
import com.clinicflow.patient.domain.ConsentStatus;
import com.clinicflow.patient.domain.ContactPreference;
import com.clinicflow.patient.domain.EmailAddress;
import com.clinicflow.patient.domain.FullName;
import com.clinicflow.patient.domain.Patient;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PatientStatus;
import com.clinicflow.patient.domain.PhoneNumber;
import com.clinicflow.patient.domain.PreferredName;
import com.clinicflow.patient.domain.PsychologistId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Set;
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
    private static final Instant JUNE_1 = Instant.parse("2026-06-01T10:00:00Z");
    private static final Instant JUNE_10 = Instant.parse("2026-06-10T10:00:00Z");

    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(org.testcontainers.utility.DockerImageName
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
    void findPageReturnsOnlyOwnedPatients() {
        // Arrange
        PsychologistId owner = new PsychologistId(UUID.randomUUID());
        Patient owned = patient(UUID.randomUUID(), owner, "owned@example.com");
        Patient other = patient(UUID.randomUUID(), new PsychologistId(UUID.randomUUID()), "other@example.com");
        persist(owned, other);

        // Act
        PageResult<Patient> found = repository.findPage(all(owner, new PageQuery(0, 20)));

        // Assert
        assertThat(found.items()).containsExactly(owned);
        assertThat(found.totalElements()).isEqualTo(1);
    }

    @Test
    void findPageOrdersByNameIgnoringCaseAndPagesThrough() {
        // Arrange
        PsychologistId owner = new PsychologistId(UUID.randomUUID());
        Patient carla = named(owner, "carla Sousa", null, "carla@example.com", ConsentStatus.GRANTED, JUNE_1);
        Patient ana = named(owner, "Ana Martins", null, "ana@example.com", ConsentStatus.GRANTED, JUNE_1);
        Patient bruno = named(owner, "Bruno Costa", null, "bruno@example.com", ConsentStatus.GRANTED, JUNE_1);
        persist(carla, ana, bruno);

        // Act
        PageResult<Patient> first = repository.findPage(all(owner, new PageQuery(0, 2)));
        PageResult<Patient> second = repository.findPage(all(owner, new PageQuery(1, 2)));

        // Assert
        assertThat(first.items()).extracting(Patient::id).containsExactly(ana.id(), bruno.id());
        assertThat(second.items()).extracting(Patient::id).containsExactly(carla.id());
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
    }

    @Test
    void findPageBreaksNameTiesByIdSoPagesNeverOverlap() {
        // Arrange
        PsychologistId owner = new PsychologistId(UUID.randomUUID());
        Patient higher = patient(UUID.fromString("00000000-0000-4000-8000-000000000002"), owner, "b@example.com");
        Patient lower = patient(UUID.fromString("00000000-0000-4000-8000-000000000001"), owner, "a@example.com");
        persist(higher, lower);

        // Act
        PageResult<Patient> first = repository.findPage(all(owner, new PageQuery(0, 1)));
        PageResult<Patient> second = repository.findPage(all(owner, new PageQuery(1, 1)));

        // Assert
        assertThat(first.items()).extracting(Patient::id).containsExactly(lower.id());
        assertThat(second.items()).extracting(Patient::id).containsExactly(higher.id());
    }

    @Test
    void findPageSearchesNamesAndEmailIgnoringCase() {
        // Arrange
        PsychologistId owner = new PsychologistId(UUID.randomUUID());
        Patient byName = named(owner, "Ana Martins", null, "first@example.com", ConsentStatus.GRANTED, JUNE_1);
        Patient byPreferred = named(owner, "Joana Reis", "MARTINHA", "second@example.com", ConsentStatus.GRANTED,
                JUNE_1);
        Patient byEmail = named(owner, "Rui Lopes", null, "rui.martins@example.com", ConsentStatus.GRANTED, JUNE_1);
        Patient unrelated = named(owner, "Bruno Costa", null, "bruno@example.com", ConsentStatus.GRANTED, JUNE_1);
        persist(byName, byPreferred, byEmail, unrelated);

        // Act
        PageResult<Patient> found = repository.findPage(search(owner, "mArTi"));

        // Assert
        assertThat(found.items()).extracting(Patient::id)
                .containsExactly(byName.id(), byPreferred.id(), byEmail.id());
    }

    @Test
    void findPageMatchesLikeWildcardsInTheSearchLiterally() {
        // Arrange
        PsychologistId owner = new PsychologistId(UUID.randomUUID());
        Patient percent = named(owner, "Ana 100% Martins", null, "percent@example.com", ConsentStatus.GRANTED, JUNE_1);
        Patient underscore = named(owner, "Ana_Martins", null, "underscore@example.com", ConsentStatus.GRANTED, JUNE_1);
        Patient plain = named(owner, "Ana Martins", null, "plain@example.com", ConsentStatus.GRANTED, JUNE_1);
        persist(percent, underscore, plain);

        // Act
        PageResult<Patient> byPercent = repository.findPage(search(owner, "0%"));
        PageResult<Patient> byUnderscore = repository.findPage(search(owner, "a_m"));
        PageResult<Patient> byBackslash = repository.findPage(search(owner, "\\"));

        // Assert
        assertThat(byPercent.items()).extracting(Patient::id).containsExactly(percent.id());
        assertThat(byUnderscore.items()).extracting(Patient::id).containsExactly(underscore.id());
        assertThat(byBackslash.items()).isEmpty();
    }

    @Test
    void findPageFiltersByStatusConsentAndCreationTime() {
        // Arrange
        PsychologistId owner = new PsychologistId(UUID.randomUUID());
        Patient match = named(owner, "Ana Match", null, "match@example.com", ConsentStatus.PENDING, JUNE_10);
        Patient tooEarly = named(owner, "Bruno Early", null, "early@example.com", ConsentStatus.PENDING, JUNE_1);
        Patient granted = named(owner, "Carla Granted", null, "granted@example.com", ConsentStatus.GRANTED, JUNE_10);
        Patient archived = named(owner, "Duarte Archived", null, "archived@example.com", ConsentStatus.PENDING,
                JUNE_10);
        archived.changeStatus(PatientStatus.ARCHIVED, JUNE_10);
        persist(match, tooEarly, granted, archived);
        var query = new ListPatientsQuery(owner, null, Set.of(PatientStatus.ACTIVE), Set.of(ConsentStatus.PENDING),
                JUNE_10, null, new PageQuery(0, 20));

        // Act
        PageResult<Patient> found = repository.findPage(query);

        // Assert
        assertThat(found.items()).extracting(Patient::id).containsExactly(match.id());
    }

    @Test
    void findPageLooksUpOwnedPatientsById() {
        // Arrange
        PsychologistId owner = new PsychologistId(UUID.randomUUID());
        Patient wanted = patient(UUID.randomUUID(), owner, "wanted@example.com");
        Patient unwanted = patient(UUID.randomUUID(), owner, "unwanted@example.com");
        Patient foreign = patient(UUID.randomUUID(), new PsychologistId(UUID.randomUUID()), "foreign@example.com");
        persist(wanted, unwanted, foreign);
        var query = new ListPatientsQuery(owner, null, null, null, null, Set.of(wanted.id(), foreign.id()),
                new PageQuery(0, 20));

        // Act
        PageResult<Patient> found = repository.findPage(query);

        // Assert
        assertThat(found.items()).extracting(Patient::id).containsExactly(wanted.id());
    }

    @Test
    void newPatientIsStoredAtVersionZero() {
        // Arrange
        Patient patient = patient(UUID.randomUUID(), new PsychologistId(UUID.randomUUID()), "new@example.com");

        // Act
        Patient saved = repository.save(patient);

        // Assert
        assertThat(saved.version()).contains(0L);
    }

    @Test
    void savingAChangedPatientIncrementsItsVersion() {
        // Arrange
        Patient stored = repository.save(
                patient(UUID.randomUUID(), new PsychologistId(UUID.randomUUID()), "changed@example.com"));
        stored.changeStatus(PatientStatus.INACTIVE, Instant.parse("2026-06-02T10:00:00Z"));

        // Act
        Patient saved = repository.save(stored);

        // Assert
        assertThat(saved.version()).contains(1L);
    }

    @Test
    void savingAnOlderVersionIsRejected() {
        // Arrange
        Patient original = repository.save(
                patient(UUID.randomUUID(), new PsychologistId(UUID.randomUUID()), "stale@example.com"));
        Patient current = repository.findById(original.id()).orElseThrow();
        current.changeStatus(PatientStatus.INACTIVE, Instant.parse("2026-06-02T10:00:00Z"));
        repository.save(current);
        original.changeStatus(PatientStatus.ARCHIVED, Instant.parse("2026-06-03T10:00:00Z"));

        // Act
        Throwable thrown = catchThrowable(() -> repository.save(original));

        // Assert
        assertThat(thrown).isInstanceOf(StaleResourceVersionException.class);
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
                JUNE_1);
    }

    private Patient named(PsychologistId owner, String fullName, String preferredName, String email,
                          ConsentStatus consent, Instant createdAt) {
        return Patient.create(new PatientId(UUID.randomUUID()), owner, new EmailAddress(email),
                new FullName(fullName), preferredName == null ? null : new PreferredName(preferredName), null,
                null, null, ContactPreference.EMAIL, consent, createdAt);
    }

    private static ListPatientsQuery all(PsychologistId owner, PageQuery page) {
        return new ListPatientsQuery(owner, null, null, null, null, null, page);
    }

    private static ListPatientsQuery search(PsychologistId owner, String search) {
        return new ListPatientsQuery(owner, search, null, null, null, null, new PageQuery(0, 20));
    }
}
