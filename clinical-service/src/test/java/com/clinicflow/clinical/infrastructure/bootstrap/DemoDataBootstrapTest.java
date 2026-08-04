package com.clinicflow.clinical.infrastructure.bootstrap;

import com.clinicflow.clinical.application.ports.CarePlanRepository;
import com.clinicflow.clinical.application.ports.ClinicalCaseRepository;
import com.clinicflow.clinical.application.ports.PracticeProfileRepository;
import com.clinicflow.clinical.application.ports.SessionRecordRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

class DemoDataBootstrapTest {
    @Test
    void canBeConstructedBySpring() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(demoBootstrapProperty());
            context.registerBean(PracticeProfileRepository.class, InMemoryPracticeProfileRepository::new);
            context.registerBean(ClinicalCaseRepository.class, InMemoryClinicalCaseRepository::new);
            context.registerBean(CarePlanRepository.class, InMemoryCarePlanRepository::new);
            context.registerBean(SessionRecordRepository.class, InMemorySessionRecordRepository::new);
            context.registerBean(DemoDataBootstrap.class);

            context.refresh();

            assertThat(context.getBean(DemoDataBootstrap.class)).isNotNull();
        }
    }

    private static MapPropertySource demoBootstrapProperty() {
        return new MapPropertySource("test", Map.of("clinicflow.demo.bootstrap.enabled", "true"));
    }

    @Test
    void seedsClinicalWorkflowIdempotently() throws Exception {
        InMemoryPracticeProfileRepository practiceProfiles = new InMemoryPracticeProfileRepository();
        InMemoryClinicalCaseRepository clinicalCases = new InMemoryClinicalCaseRepository();
        InMemoryCarePlanRepository carePlans = new InMemoryCarePlanRepository();
        InMemorySessionRecordRepository sessionRecords = new InMemorySessionRecordRepository();
        DemoDataBootstrap bootstrap = new DemoDataBootstrap(
                practiceProfiles,
                clinicalCases,
                carePlans,
                sessionRecords,
                Clock.fixed(Instant.parse("2026-07-05T10:00:00Z"), ZoneOffset.UTC)
        );

        bootstrap.run(null);
        bootstrap.run(null);

        assertThat(practiceProfiles.records()).hasSize(2);
        assertThat(clinicalCases.records()).hasSize(6);
        assertThat(carePlans.records()).hasSize(6);
        assertThat(sessionRecords.records()).hasSize(5);
        assertThat(clinicalCases.records().values()).extracting(ClinicalCase::status)
                .contains(ClinicalCaseStatus.INTAKE, ClinicalCaseStatus.ACTIVE,
                        ClinicalCaseStatus.PAUSED, ClinicalCaseStatus.DISCHARGED);
        assertThat(sessionRecords.records().values()).extracting(SessionRecord::noteStatus)
                .contains(NoteStatus.PENDING_NOTE, NoteStatus.DRAFT, NoteStatus.SIGNED);
    }

    private static final class InMemoryPracticeProfileRepository implements PracticeProfileRepository {
        private final Map<PsychologistId, PracticeProfile> records = new LinkedHashMap<>();

        @Override
        public Optional<PracticeProfile> findByPsychologistId(PsychologistId psychologistId) {
            return Optional.ofNullable(records.get(psychologistId));
        }

        @Override
        public void save(PracticeProfile profile) {
            records.put(profile.psychologistId(), profile);
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
        public List<ClinicalCase> findByPsychologistId(PsychologistId psychologistId) {
            return records.values().stream()
                    .filter(clinicalCase -> clinicalCase.psychologistId().equals(psychologistId))
                    .toList();
        }

        @Override
        public List<ClinicalCase> findByPsychologistIdAndPatientId(PsychologistId psychologistId, PatientId patientId) {
            return records.values().stream()
                    .filter(clinicalCase -> clinicalCase.psychologistId().equals(psychologistId))
                    .filter(clinicalCase -> clinicalCase.patientId().equals(patientId))
                    .toList();
        }

        @Override
        public List<ClinicalCase> findByPsychologistIdAndStatus(PsychologistId psychologistId,
                                                                ClinicalCaseStatus status) {
            return records.values().stream()
                    .filter(clinicalCase -> clinicalCase.psychologistId().equals(psychologistId))
                    .filter(clinicalCase -> clinicalCase.status() == status)
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
        public void save(ClinicalCase clinicalCase) {
            records.put(clinicalCase.id(), clinicalCase);
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
        public void save(CarePlan carePlan) {
            records.put(carePlan.clinicalCaseId(), carePlan);
        }

        Map<ClinicalCaseId, CarePlan> records() {
            return records;
        }
    }

    private static final class InMemorySessionRecordRepository implements SessionRecordRepository {
        private final Map<SessionRecordId, SessionRecord> records = new LinkedHashMap<>();

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
        public List<SessionRecord> findByClinicalCaseId(ClinicalCaseId clinicalCaseId) {
            return records.values().stream()
                    .filter(record -> record.clinicalCaseId().filter(clinicalCaseId::equals).isPresent())
                    .toList();
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
        public void save(SessionRecord sessionRecord) {
            records.put(sessionRecord.id(), sessionRecord);
        }

        Map<SessionRecordId, SessionRecord> records() {
            return records;
        }
    }
}
