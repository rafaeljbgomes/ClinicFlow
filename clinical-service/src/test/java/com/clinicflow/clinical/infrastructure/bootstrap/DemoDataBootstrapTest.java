package com.clinicflow.clinical.infrastructure.bootstrap;

import com.clinicflow.clinical.application.ports.CarePlanRepository;
import com.clinicflow.clinical.application.ports.ClinicalCaseRepository;
import com.clinicflow.clinical.application.ports.PracticeProfileRepository;
import com.clinicflow.clinical.application.ports.SessionRecordRepository;
import com.clinicflow.clinical.application.queries.ListClinicalCasesQuery;
import com.clinicflow.clinical.application.queries.PageQuery;
import com.clinicflow.clinical.application.results.PageResult;
import com.clinicflow.clinical.domain.AppointmentId;
import com.clinicflow.clinical.domain.CarePlan;
import com.clinicflow.clinical.domain.ClinicalCase;
import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.ClinicalCaseStatus;
import com.clinicflow.clinical.domain.NoteStatus;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PracticeProfile;
import com.clinicflow.clinical.domain.PsychologistId;
import com.clinicflow.clinical.domain.SessionRecord;
import com.clinicflow.clinical.domain.SessionRecordId;
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
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class DemoDataBootstrapTest {
    private static final Clock FIRST_START = Clock.fixed(Instant.parse("2026-07-05T10:00:00Z"), ZoneOffset.UTC);
    private static final Clock RESTART = Clock.fixed(Instant.parse("2026-07-09T10:00:00Z"), ZoneOffset.UTC);
    private static final SessionRecordId SEEDED_DRAFT_ID =
            new SessionRecordId(UUID.fromString("33000000-0000-4000-8000-000000000005"));

    private final InMemoryPracticeProfileRepository practiceProfiles = new InMemoryPracticeProfileRepository();
    private final InMemoryClinicalCaseRepository clinicalCases = new InMemoryClinicalCaseRepository();
    private final InMemoryCarePlanRepository carePlans = new InMemoryCarePlanRepository();
    private final InMemorySessionRecordRepository sessionRecords = new InMemorySessionRecordRepository();

    @Test
    void canBeConstructedBySpring() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            // Arrange
            context.getEnvironment().getPropertySources().addFirst(demoBootstrapProperty());
            context.registerBean(PracticeProfileRepository.class, InMemoryPracticeProfileRepository::new);
            context.registerBean(ClinicalCaseRepository.class, InMemoryClinicalCaseRepository::new);
            context.registerBean(CarePlanRepository.class, InMemoryCarePlanRepository::new);
            context.registerBean(SessionRecordRepository.class, InMemorySessionRecordRepository::new);
            context.registerBean(DemoDataBootstrap.class);

            // Act
            context.refresh();

            // Assert
            assertThat(context.getBean(DemoDataBootstrap.class)).isNotNull();
        }
    }

    @Test
    void seedsTheDemoClinicalWorkflow() {
        // Act
        bootstrap(FIRST_START).run(null);

        // Assert
        assertThat(practiceProfiles.records()).hasSize(2);
        assertThat(clinicalCases.records()).hasSize(6);
        assertThat(carePlans.records()).hasSize(6);
        assertThat(sessionRecords.records()).hasSize(5);
    }

    @Test
    void seedsCasesInEveryLifecycleStatus() {
        // Act
        bootstrap(FIRST_START).run(null);

        // Assert
        assertThat(clinicalCases.records().values()).extracting(ClinicalCase::status)
                .contains(ClinicalCaseStatus.INTAKE, ClinicalCaseStatus.ACTIVE,
                        ClinicalCaseStatus.PAUSED, ClinicalCaseStatus.DISCHARGED);
    }

    @Test
    void seedsSessionRecordsInEveryNoteStatus() {
        // Act
        bootstrap(FIRST_START).run(null);

        // Assert
        assertThat(sessionRecords.records().values()).extracting(SessionRecord::noteStatus)
                .contains(NoteStatus.PENDING_NOTE, NoteStatus.DRAFT, NoteStatus.SIGNED);
    }

    @Test
    void seedsOneSignedNoteSignedByItsTreatingPsychologist() {
        // Act
        bootstrap(FIRST_START).run(null);

        // Assert
        assertThat(sessionRecords.records().values())
                .filteredOn(record -> record.noteStatus() == NoteStatus.SIGNED)
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.signedBy()).contains(record.psychologistId());
                    assertThat(record.signedAt()).contains(Instant.parse("2026-06-25T12:00:00Z"));
                    assertThat(record.updatedAt()).isEqualTo(record.signedAt().orElseThrow());
                });
    }

    @Test
    void rerunDoesNotSaveSessionRecordsAgain() {
        // Arrange
        DemoDataBootstrap bootstrap = bootstrap(FIRST_START);
        bootstrap.run(null);

        // Act
        bootstrap.run(null);

        // Assert
        assertThat(sessionRecords.saves()).isEqualTo(5);
    }

    @Test
    void rerunDoesNotDuplicateSeededAggregates() {
        // Arrange
        DemoDataBootstrap bootstrap = bootstrap(FIRST_START);
        bootstrap.run(null);

        // Act
        bootstrap.run(null);

        // Assert
        assertThat(practiceProfiles.records()).hasSize(2);
        assertThat(clinicalCases.records()).hasSize(6);
        assertThat(carePlans.records()).hasSize(6);
        assertThat(sessionRecords.records()).hasSize(5);
    }

    @Test
    void rerunUpdatesTheStoredAggregatesInPlace() {
        // Arrange
        DemoDataBootstrap bootstrap = bootstrap(FIRST_START);
        bootstrap.run(null);

        // Act
        bootstrap.run(null);

        // Assert
        assertThat(practiceProfiles.records().values()).extracting(profile -> profile.version().orElseThrow())
                .containsOnly(1L);
        assertThat(clinicalCases.records().values()).extracting(clinicalCase -> clinicalCase.version().orElseThrow())
                .containsOnly(1L);
        assertThat(carePlans.records().values()).extracting(carePlan -> carePlan.version().orElseThrow())
                .containsOnly(1L);
    }

    @Test
    void restartKeepsExistingSessionRecordsSoEditedNotesSurvive() {
        // Arrange
        bootstrap(FIRST_START).run(null);
        signSeededDraft();
        Map<SessionRecordId, SessionRecord> beforeRestart = new LinkedHashMap<>(sessionRecords.records());

        // Act
        restartedBootstrap().run(null);

        // Assert
        assertThat(sessionRecords.saves()).isEqualTo(5);
        beforeRestart.forEach((id, record) -> assertThat(sessionRecords.records().get(id)).isSameAs(record));
    }

    @Test
    void restartKeepsANoteSignedInTheDemoSigned() {
        // Arrange
        bootstrap(FIRST_START).run(null);
        signSeededDraft();

        // Act
        restartedBootstrap().run(null);

        // Assert
        assertThat(sessionRecords.records().get(SEEDED_DRAFT_ID).noteStatus()).isEqualTo(NoteStatus.SIGNED);
    }

    private DemoDataBootstrap bootstrap(Clock clock) {
        return new DemoDataBootstrap(practiceProfiles, clinicalCases, carePlans, sessionRecords, clock);
    }

    // A restart keeps only the session store so the test isolates how session records are reconciled.
    private DemoDataBootstrap restartedBootstrap() {
        return new DemoDataBootstrap(new InMemoryPracticeProfileRepository(), new InMemoryClinicalCaseRepository(),
                new InMemoryCarePlanRepository(), sessionRecords, RESTART);
    }

    private void signSeededDraft() {
        SessionRecord draft = sessionRecords.findById(SEEDED_DRAFT_ID).orElseThrow(() ->
                new IllegalStateException("Arrange step \"find seeded draft\" found no session record"));
        draft.sign(draft.psychologistId(), Instant.parse("2026-07-06T10:00:00Z"));
    }

    // Like persistence: a save must name the stored version, or none for a record that is not stored yet.
    private static <T> void requireStoredVersion(T stored, Function<T, Optional<Long>> version,
                                                 Optional<Long> saving) {
        Optional<Long> current = stored == null ? Optional.empty() : version.apply(stored);
        if (!current.equals(saving)) {
            throw new IllegalStateException("Stale or duplicate demo record");
        }
    }

    private static long nextVersion(Optional<Long> version) {
        return version.map(current -> current + 1).orElse(0L);
    }

    private static MapPropertySource demoBootstrapProperty() {
        return new MapPropertySource("test", Map.of("clinicflow.demo.bootstrap.enabled", "true"));
    }

    private static final class InMemoryPracticeProfileRepository implements PracticeProfileRepository {
        private final Map<PsychologistId, PracticeProfile> records = new LinkedHashMap<>();

        @Override
        public Optional<PracticeProfile> findByPsychologistId(PsychologistId psychologistId) {
            return Optional.ofNullable(records.get(psychologistId));
        }

        @Override
        public PracticeProfile save(PracticeProfile profile) {
            requireStoredVersion(records.get(profile.psychologistId()), PracticeProfile::version, profile.version());
            PracticeProfile saved = PracticeProfile.rehydrate(profile.psychologistId(),
                    profile.professionalRegistration(), profile.timezone(), profile.defaultSessionDurationMinutes(),
                    profile.primaryLocation(), profile.telehealthEnabled(), profile.createdAt(), profile.updatedAt(),
                    nextVersion(profile.version()));
            records.put(saved.psychologistId(), saved);
            return saved;
        }

        Map<PsychologistId, PracticeProfile> records() {
            return records;
        }
    }

    private static final class InMemoryClinicalCaseRepository implements ClinicalCaseRepository {
        private final Map<ClinicalCaseId, ClinicalCase> records = new LinkedHashMap<>();

        @Override
        public Optional<ClinicalCase> findById(ClinicalCaseId id) {
            return Optional.ofNullable(records.get(id));
        }

        @Override
        public PageResult<ClinicalCase> findPage(ListClinicalCasesQuery query) {
            throw new UnsupportedOperationException("The bootstrap never lists clinical cases");
        }

        @Override
        public List<ClinicalCase> findByPsychologistIdAndPatientId(PsychologistId psychologistId, PatientId patientId) {
            return records.values().stream()
                    .filter(clinicalCase -> clinicalCase.psychologistId().equals(psychologistId))
                    .filter(clinicalCase -> clinicalCase.patientId().equals(patientId))
                    .toList();
        }

        @Override
        public Optional<ClinicalCase> findOpenByPsychologistIdAndPatientId(PsychologistId psychologistId,
                                                                           PatientId patientId) {
            return records.values().stream()
                    .filter(clinicalCase -> clinicalCase.psychologistId().equals(psychologistId))
                    .filter(clinicalCase -> clinicalCase.patientId().equals(patientId))
                    .filter(clinicalCase -> clinicalCase.status() != ClinicalCaseStatus.DISCHARGED)
                    .findFirst();
        }

        @Override
        public boolean existsOpenByPsychologistIdAndPatientId(PsychologistId psychologistId, PatientId patientId) {
            return findOpenByPsychologistIdAndPatientId(psychologistId, patientId).isPresent();
        }

        @Override
        public ClinicalCase save(ClinicalCase clinicalCase) {
            requireStoredVersion(records.get(clinicalCase.id()), ClinicalCase::version, clinicalCase.version());
            ClinicalCase saved = ClinicalCase.rehydrate(clinicalCase.id(), clinicalCase.psychologistId(),
                    clinicalCase.patientId(), clinicalCase.presentingConcern(), clinicalCase.status(),
                    clinicalCase.openedAt(), clinicalCase.closedAt(), clinicalCase.createdAt(),
                    clinicalCase.updatedAt(), nextVersion(clinicalCase.version()));
            records.put(saved.id(), saved);
            return saved;
        }

        Map<ClinicalCaseId, ClinicalCase> records() {
            return records;
        }
    }

    private static final class InMemoryCarePlanRepository implements CarePlanRepository {
        private final Map<ClinicalCaseId, CarePlan> records = new LinkedHashMap<>();

        @Override
        public Optional<CarePlan> findByClinicalCaseId(ClinicalCaseId clinicalCaseId) {
            return Optional.ofNullable(records.get(clinicalCaseId));
        }

        @Override
        public CarePlan save(CarePlan carePlan) {
            requireStoredVersion(records.get(carePlan.clinicalCaseId()), CarePlan::version, carePlan.version());
            CarePlan saved = CarePlan.rehydrate(carePlan.id(), carePlan.clinicalCaseId(), carePlan.psychologistId(),
                    carePlan.patientId(), carePlan.therapeuticFocus(), carePlan.plannedFrequency(),
                    carePlan.reviewDate(), carePlan.goals(), carePlan.createdAt(), carePlan.updatedAt(),
                    nextVersion(carePlan.version()));
            records.put(saved.clinicalCaseId(), saved);
            return saved;
        }

        Map<ClinicalCaseId, CarePlan> records() {
            return records;
        }
    }

    private static final class InMemorySessionRecordRepository implements SessionRecordRepository {
        private final Map<SessionRecordId, SessionRecord> records = new LinkedHashMap<>();
        private int saves;

        @Override
        public Optional<SessionRecord> findById(SessionRecordId id) {
            return Optional.ofNullable(records.get(id));
        }

        @Override
        public Optional<SessionRecord> findByAppointmentId(AppointmentId appointmentId) {
            return records.values().stream()
                    .filter(record -> record.appointmentId().filter(appointmentId::equals).isPresent())
                    .findFirst();
        }

        @Override
        public PageResult<SessionRecord> findPageByClinicalCaseId(ClinicalCaseId clinicalCaseId, PageQuery page) {
            throw new UnsupportedOperationException("The bootstrap never lists session records");
        }

        @Override
        public List<SessionRecord> findByPsychologistIdAndPatientId(PsychologistId psychologistId,
                                                                    PatientId patientId) {
            return records.values().stream()
                    .filter(record -> record.psychologistId().equals(psychologistId))
                    .filter(record -> record.patientId().equals(patientId))
                    .toList();
        }

        @Override
        public SessionRecord save(SessionRecord sessionRecord) {
            saves++;
            records.put(sessionRecord.id(), sessionRecord);
            return sessionRecord;
        }

        Map<SessionRecordId, SessionRecord> records() {
            return records;
        }

        int saves() {
            return saves;
        }
    }
}
