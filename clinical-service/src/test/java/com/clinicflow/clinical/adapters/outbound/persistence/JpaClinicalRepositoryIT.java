package com.clinicflow.clinical.adapters.outbound.persistence;

import com.clinicflow.clinical.application.queries.ListClinicalCasesQuery;
import com.clinicflow.clinical.application.queries.PageQuery;
import com.clinicflow.clinical.application.results.PageResult;
import com.clinicflow.clinical.domain.AppointmentId;
import com.clinicflow.clinical.domain.CareGoal;
import com.clinicflow.clinical.domain.CareGoalId;
import com.clinicflow.clinical.domain.CarePlan;
import com.clinicflow.clinical.domain.CarePlanId;
import com.clinicflow.clinical.domain.ClinicalCase;
import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.ClinicalCaseStatus;
import com.clinicflow.clinical.domain.GoalStatus;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PracticeProfile;
import com.clinicflow.clinical.domain.PsychologistId;
import com.clinicflow.clinical.domain.SessionModality;
import com.clinicflow.clinical.domain.SessionRecord;
import com.clinicflow.clinical.domain.SessionRecordId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaPracticeProfileRepository.class, JpaClinicalCaseRepository.class, JpaCarePlanRepository.class,
        JpaSessionRecordRepository.class, ClinicalPersistenceMapperImpl.class})
@Testcontainers
class JpaClinicalRepositoryIT {
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired private JpaPracticeProfileRepository profiles;
    @Autowired private JpaClinicalCaseRepository cases;
    @Autowired private JpaCarePlanRepository plans;
    @Autowired private JpaSessionRecordRepository sessions;
    @Autowired private SpringDataClinicalCaseJpaRepository caseDelegate;

    private final PsychologistId psychologistId = new PsychologistId(UUID.randomUUID());
    private final PatientId patientId = new PatientId(UUID.randomUUID());

    @Test
    void findsPracticeProfileByPsychologist() {
        // Arrange
        PracticeProfile profile = PracticeProfile.create(psychologistId, "OPP 123",
                ZoneId.of("Europe/Lisbon"), 50, "Lisbon", true, NOW);
        profiles.save(profile);

        // Act
        Optional<PracticeProfile> found = profiles.findByPsychologistId(psychologistId);

        // Assert
        assertThat(found).contains(profile);
    }

    @Test
    void findsOpenCaseByPsychologistAndPatient() {
        // Arrange
        ClinicalCase clinicalCase = persistedCase();

        // Act
        Optional<ClinicalCase> found = cases.findOpenByPsychologistIdAndPatientId(psychologistId, patientId);

        // Assert
        assertThat(found).contains(clinicalCase);
    }

    @Test
    void findsCarePlanWithItsGoalsByCase() {
        // Arrange
        ClinicalCase clinicalCase = persistedCase();
        plans.save(CarePlan.create(new CarePlanId(UUID.randomUUID()), clinicalCase,
                "Reduce avoidance", "Weekly", LocalDate.of(2026, 7, 1),
                List.of(new CareGoal(new CareGoalId(UUID.randomUUID()), "Practice exposure",
                        null, GoalStatus.IN_PROGRESS, 20)), NOW));

        // Act
        Optional<CarePlan> found = plans.findByClinicalCaseId(clinicalCase.id());

        // Assert
        assertThat(found).hasValueSatisfying(plan -> assertThat(plan.goals()).hasSize(1));
    }

    @Test
    void findsSessionRecordByAppointment() {
        // Arrange
        AppointmentId appointmentId = new AppointmentId(UUID.randomUUID());
        SessionRecord session = persistedSession(persistedCase(), appointmentId);

        // Act
        Optional<SessionRecord> found = sessions.findByAppointmentId(appointmentId);

        // Assert
        assertThat(found).contains(session);
    }

    @Test
    void findsSessionHistoryByPsychologistAndPatient() {
        // Arrange
        SessionRecord session = persistedSession(persistedCase(), new AppointmentId(UUID.randomUUID()));

        // Act
        List<SessionRecord> found = sessions.findByPsychologistIdAndPatientId(psychologistId, patientId);

        // Assert
        assertThat(found).containsExactly(session);
    }

    @Test
    void findPageListsOnlyOwnedCasesMostRecentlyOpenedFirst() {
        // Arrange
        ClinicalCase oldest = persistedCase(psychologistId, new PatientId(UUID.randomUUID()), at(1));
        ClinicalCase newest = persistedCase(psychologistId, new PatientId(UUID.randomUUID()), at(3));
        ClinicalCase middle = persistedCase(psychologistId, new PatientId(UUID.randomUUID()), at(2));
        persistedCase(new PsychologistId(UUID.randomUUID()), patientId, at(4));

        // Act
        PageResult<ClinicalCase> found = cases.findPage(caseQuery(null, Set.of(), 0, 20));

        // Assert
        assertThat(found.items()).containsExactly(newest, middle, oldest);
        assertThat(found.totalElements()).isEqualTo(3);
    }

    @Test
    void findPageFiltersCasesByPatientAndStatuses() {
        // Arrange
        ClinicalCase intake = persistedCase(psychologistId, patientId, at(1));
        ClinicalCase discharged = ClinicalCase.create(new ClinicalCaseId(UUID.randomUUID()), psychologistId,
                patientId, "Earlier episode", at(0)).changeStatus(ClinicalCaseStatus.DISCHARGED, at(0));
        cases.save(discharged);
        ClinicalCase otherPatientActive = ClinicalCase.create(new ClinicalCaseId(UUID.randomUUID()), psychologistId,
                new PatientId(UUID.randomUUID()), "Low mood", at(2)).changeStatus(ClinicalCaseStatus.ACTIVE, at(2));
        cases.save(otherPatientActive);

        // Act
        PageResult<ClinicalCase> byPatient = cases.findPage(caseQuery(patientId, Set.of(), 0, 20));
        PageResult<ClinicalCase> open = cases.findPage(caseQuery(null,
                Set.of(ClinicalCaseStatus.INTAKE, ClinicalCaseStatus.ACTIVE), 0, 20));
        PageResult<ClinicalCase> patientDischarged = cases.findPage(caseQuery(patientId,
                Set.of(ClinicalCaseStatus.DISCHARGED), 0, 20));

        // Assert
        assertThat(byPatient.items()).containsExactly(intake, discharged);
        assertThat(open.items()).containsExactly(otherPatientActive, intake);
        assertThat(patientDischarged.items()).containsExactly(discharged);
    }

    @Test
    void findPageSplitsCasesOpenedAtTheSameInstantAcrossPagesWithoutOverlap() {
        // Arrange
        List<ClinicalCase> sameInstant = List.of(
                persistedCase(psychologistId, new PatientId(UUID.randomUUID()), at(1)),
                persistedCase(psychologistId, new PatientId(UUID.randomUUID()), at(1)),
                persistedCase(psychologistId, new PatientId(UUID.randomUUID()), at(1)));

        // Act
        PageResult<ClinicalCase> page0 = cases.findPage(caseQuery(null, Set.of(), 0, 2));
        PageResult<ClinicalCase> page1 = cases.findPage(caseQuery(null, Set.of(), 1, 2));

        // Assert
        assertThat(page0.items()).hasSize(2);
        assertThat(page1.items()).hasSize(1);
        assertThat(concat(page0.items(), page1.items())).containsExactlyInAnyOrderElementsOf(sameInstant);
        assertThat(page0.totalPages()).isEqualTo(2);
    }

    @Test
    void findPageByClinicalCaseIdListsOnlyTheCaseRecordsLatestSessionFirst() {
        // Arrange
        ClinicalCase clinicalCase = persistedCase();
        SessionRecord earlier = persistedSession(clinicalCase, new AppointmentId(UUID.randomUUID()), at(1));
        SessionRecord later = persistedSession(clinicalCase, new AppointmentId(UUID.randomUUID()), at(2));
        ClinicalCase otherCase = persistedCase(psychologistId, new PatientId(UUID.randomUUID()), NOW);
        persistedSession(otherCase, new AppointmentId(UUID.randomUUID()), at(3));

        // Act
        PageResult<SessionRecord> found = sessions.findPageByClinicalCaseId(clinicalCase.id(), new PageQuery(0, 20));

        // Assert
        assertThat(found.items()).containsExactly(later, earlier);
        assertThat(found.totalElements()).isEqualTo(2);
    }

    @Test
    void findPageByClinicalCaseIdSplitsRecordsAcrossPages() {
        // Arrange
        ClinicalCase clinicalCase = persistedCase();
        SessionRecord first = persistedSession(clinicalCase, new AppointmentId(UUID.randomUUID()), at(1));
        SessionRecord second = persistedSession(clinicalCase, new AppointmentId(UUID.randomUUID()), at(2));
        SessionRecord third = persistedSession(clinicalCase, new AppointmentId(UUID.randomUUID()), at(3));

        // Act
        PageResult<SessionRecord> page0 = sessions.findPageByClinicalCaseId(clinicalCase.id(), new PageQuery(0, 2));
        PageResult<SessionRecord> page1 = sessions.findPageByClinicalCaseId(clinicalCase.id(), new PageQuery(1, 2));

        // Assert
        assertThat(page0.items()).containsExactly(third, second);
        assertThat(page1.items()).containsExactly(first);
        assertThat(page1.totalPages()).isEqualTo(2);
    }

    @Test
    void flywayPartialUniqueIndexRejectsSecondOpenCaseForPatient() {
        // Arrange
        cases.save(ClinicalCase.create(new ClinicalCaseId(UUID.randomUUID()), psychologistId,
                patientId, "First concern", NOW));
        ClinicalCase secondOpenCase = ClinicalCase.create(new ClinicalCaseId(UUID.randomUUID()),
                psychologistId, patientId, "Second concern", NOW);

        // Act
        Throwable thrown = catchThrowable(() -> {
            cases.save(secondOpenCase);
            caseDelegate.flush();
        });

        // Assert
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
    }

    private ClinicalCase persistedCase() {
        return persistedCase(psychologistId, patientId, NOW);
    }

    private ClinicalCase persistedCase(PsychologistId owner, PatientId patient, Instant openedAt) {
        ClinicalCase clinicalCase = ClinicalCase.create(new ClinicalCaseId(UUID.randomUUID()), owner,
                patient, "Anxiety symptoms", openedAt);
        cases.save(clinicalCase);
        return clinicalCase;
    }

    private SessionRecord persistedSession(ClinicalCase clinicalCase, AppointmentId appointmentId) {
        return persistedSession(clinicalCase, appointmentId, NOW);
    }

    private SessionRecord persistedSession(ClinicalCase clinicalCase, AppointmentId appointmentId,
                                           Instant sessionDate) {
        SessionRecord session = SessionRecord.fromCompletedAppointment(new SessionRecordId(UUID.randomUUID()),
                clinicalCase.id(), clinicalCase.psychologistId(), clinicalCase.patientId(), appointmentId,
                sessionDate, SessionModality.ONLINE, 50, NOW);
        sessions.save(session);
        return session;
    }

    private ListClinicalCasesQuery caseQuery(PatientId patient, Set<ClinicalCaseStatus> statuses, int page,
                                             int size) {
        return new ListClinicalCasesQuery(psychologistId, patient, statuses, new PageQuery(page, size));
    }

    private static Instant at(int days) {
        return NOW.plusSeconds(86_400L * days);
    }

    private static <T> List<T> concat(List<T> first, List<T> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }
}
