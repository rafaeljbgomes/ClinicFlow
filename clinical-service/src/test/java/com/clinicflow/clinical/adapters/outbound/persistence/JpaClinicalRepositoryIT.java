package com.clinicflow.clinical.adapters.outbound.persistence;

import com.clinicflow.clinical.domain.AppointmentId;
import com.clinicflow.clinical.domain.AttendanceStatus;
import com.clinicflow.clinical.domain.CareGoal;
import com.clinicflow.clinical.domain.CareGoalId;
import com.clinicflow.clinical.domain.CarePlan;
import com.clinicflow.clinical.domain.CarePlanId;
import com.clinicflow.clinical.domain.ClinicalCase;
import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.GoalStatus;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PracticeProfile;
import com.clinicflow.clinical.domain.PsychologistId;
import com.clinicflow.clinical.domain.SessionModality;
import com.clinicflow.clinical.domain.SessionRecord;
import com.clinicflow.clinical.domain.SessionRecordId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaPracticeProfileRepository.class, JpaClinicalCaseRepository.class, JpaCarePlanRepository.class,
        JpaSessionRecordRepository.class, ClinicalPersistenceMapperImpl.class})
@Testcontainers
class JpaClinicalRepositoryIT {
    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired private JpaPracticeProfileRepository profiles;
    @Autowired private JpaClinicalCaseRepository cases;
    @Autowired private JpaCarePlanRepository plans;
    @Autowired private JpaSessionRecordRepository sessions;
    @Autowired private SpringDataClinicalCaseJpaRepository caseDelegate;

    @Test
    void persistsClinicalWorkflowAndQueriesHistorySlices() {
        PsychologistId psychologistId = new PsychologistId(UUID.randomUUID());
        PatientId patientId = new PatientId(UUID.randomUUID());
        Instant now = Instant.parse("2026-06-01T10:00:00Z");
        PracticeProfile profile = PracticeProfile.create(psychologistId, "OPP 123",
                ZoneId.of("Europe/Lisbon"), 50, "Lisbon", true, now);
        ClinicalCase clinicalCase = ClinicalCase.create(new ClinicalCaseId(UUID.randomUUID()), psychologistId,
                patientId, "Anxiety symptoms", now);
        CarePlan carePlan = CarePlan.create(new CarePlanId(UUID.randomUUID()), clinicalCase,
                "Reduce avoidance", "Weekly", LocalDate.of(2026, 7, 1),
                List.of(new CareGoal(new CareGoalId(UUID.randomUUID()), "Practice exposure",
                        null, GoalStatus.IN_PROGRESS, 20)), now);
        AppointmentId appointmentId = new AppointmentId(UUID.randomUUID());
        SessionRecord session = SessionRecord.fromCompletedAppointment(new SessionRecordId(UUID.randomUUID()),
                clinicalCase.id(), psychologistId, patientId, appointmentId, now,
                SessionModality.ONLINE, 50, now);

        profiles.save(profile);
        cases.save(clinicalCase);
        plans.save(carePlan);
        sessions.save(session);

        assertThat(profiles.findByPsychologistId(psychologistId)).contains(profile);
        assertThat(cases.findOpenByPsychologistIdAndPatientId(psychologistId, patientId)).contains(clinicalCase);
        assertThat(plans.findByClinicalCaseId(clinicalCase.id()).orElseThrow().goals()).hasSize(1);
        assertThat(sessions.findByAppointmentId(appointmentId)).contains(session);
        assertThat(sessions.findByPsychologistIdAndPatientId(psychologistId, patientId)).containsExactly(session);
    }

    @Test
    void flywayPartialUniqueIndexRejectsSecondOpenCaseForPatient() {
        PsychologistId psychologistId = new PsychologistId(UUID.randomUUID());
        PatientId patientId = new PatientId(UUID.randomUUID());
        Instant now = Instant.parse("2026-06-01T10:00:00Z");

        cases.save(ClinicalCase.create(new ClinicalCaseId(UUID.randomUUID()), psychologistId,
                patientId, "First concern", now));

        assertThatThrownBy(() -> {
            cases.save(ClinicalCase.create(new ClinicalCaseId(UUID.randomUUID()),
                    psychologistId, patientId, "Second concern", now));
            caseDelegate.flush();
        })
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
