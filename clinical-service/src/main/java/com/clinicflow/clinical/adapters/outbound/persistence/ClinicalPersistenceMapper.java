package com.clinicflow.clinical.adapters.outbound.persistence;

import com.clinicflow.clinical.application.mapping.MapperConfiguration;
import com.clinicflow.clinical.domain.AppointmentId;
import com.clinicflow.clinical.domain.CareGoal;
import com.clinicflow.clinical.domain.CareGoalId;
import com.clinicflow.clinical.domain.CarePlan;
import com.clinicflow.clinical.domain.CarePlanId;
import com.clinicflow.clinical.domain.ClinicalCase;
import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PracticeProfile;
import com.clinicflow.clinical.domain.PsychologistId;
import com.clinicflow.clinical.domain.SessionRecord;
import com.clinicflow.clinical.domain.SessionRecordId;
import org.mapstruct.Mapper;

import java.time.ZoneId;

@Mapper(config = MapperConfiguration.class)
public abstract class ClinicalPersistenceMapper {
    public JpaPracticeProfileEntity toJpa(PracticeProfile profile) {
        return new JpaPracticeProfileEntity(profile.psychologistId().value(), profile.professionalRegistration(),
                profile.timezone().getId(), profile.defaultSessionDurationMinutes(), profile.primaryLocation(),
                profile.telehealthEnabled(), profile.createdAt(), profile.updatedAt());
    }

    public PracticeProfile toDomain(JpaPracticeProfileEntity entity) {
        return PracticeProfile.rehydrate(new PsychologistId(entity.psychologistId()),
                entity.professionalRegistration(), ZoneId.of(entity.timezone()),
                entity.defaultSessionDurationMinutes(), entity.primaryLocation(), entity.telehealthEnabled(),
                entity.createdAt(), entity.updatedAt());
    }

    public JpaClinicalCaseEntity toJpa(ClinicalCase clinicalCase) {
        return new JpaClinicalCaseEntity(clinicalCase.id().value(), clinicalCase.psychologistId().value(),
                clinicalCase.patientId().value(), clinicalCase.presentingConcern(), clinicalCase.status(),
                clinicalCase.openedAt(), clinicalCase.closedAt(), clinicalCase.createdAt(),
                clinicalCase.updatedAt());
    }

    public ClinicalCase toDomain(JpaClinicalCaseEntity entity) {
        return ClinicalCase.rehydrate(new ClinicalCaseId(entity.id()), new PsychologistId(entity.psychologistId()),
                new PatientId(entity.patientId()), entity.presentingConcern(), entity.status(), entity.openedAt(),
                entity.closedAt(), entity.createdAt(), entity.updatedAt());
    }

    public JpaCarePlanEntity toJpa(CarePlan carePlan) {
        return new JpaCarePlanEntity(carePlan.id().value(), carePlan.clinicalCaseId().value(),
                carePlan.psychologistId().value(), carePlan.patientId().value(), carePlan.therapeuticFocus(),
                carePlan.plannedFrequency(), carePlan.reviewDate(),
                carePlan.goals().stream().map(this::toJpa).toList(), carePlan.createdAt(), carePlan.updatedAt());
    }

    public CarePlan toDomain(JpaCarePlanEntity entity) {
        return CarePlan.rehydrate(new CarePlanId(entity.id()), new ClinicalCaseId(entity.clinicalCaseId()),
                new PsychologistId(entity.psychologistId()), new PatientId(entity.patientId()),
                entity.therapeuticFocus(), entity.plannedFrequency(), entity.reviewDate(),
                entity.goals().stream().map(this::toDomain).toList(), entity.createdAt(), entity.updatedAt());
    }

    public JpaCareGoalEntity toJpa(CareGoal goal) {
        return new JpaCareGoalEntity(goal.id().value(), goal.description(), goal.targetDate(),
                goal.status(), goal.progressPercentage());
    }

    public CareGoal toDomain(JpaCareGoalEntity entity) {
        return new CareGoal(new CareGoalId(entity.id()), entity.description(), entity.targetDate(),
                entity.status(), entity.progressPercentage());
    }

    public JpaSessionRecordEntity toJpa(SessionRecord session) {
        return new JpaSessionRecordEntity(session.id().value(),
                session.clinicalCaseId().map(ClinicalCaseId::value).orElse(null),
                session.psychologistId().value(), session.patientId().value(),
                session.appointmentId().map(AppointmentId::value).orElse(null), session.sessionDate(),
                session.modality(), session.durationMinutes(), session.attendanceStatus(), session.noteStatus(),
                session.summary(), session.focusAreas(), session.interventions(), session.homework(),
                session.nextSteps(), session.createdAt(), session.updatedAt());
    }

    public SessionRecord toDomain(JpaSessionRecordEntity entity) {
        return SessionRecord.rehydrate(new SessionRecordId(entity.id()),
                entity.clinicalCaseId() == null ? null : new ClinicalCaseId(entity.clinicalCaseId()),
                new PsychologistId(entity.psychologistId()), new PatientId(entity.patientId()),
                entity.appointmentId() == null ? null : new AppointmentId(entity.appointmentId()),
                entity.sessionDate(), entity.modality(), entity.durationMinutes(), entity.attendanceStatus(),
                entity.noteStatus(), entity.summary(), entity.focusAreas(), entity.interventions(),
                entity.homework(), entity.nextSteps(), entity.createdAt(), entity.updatedAt());
    }
}
