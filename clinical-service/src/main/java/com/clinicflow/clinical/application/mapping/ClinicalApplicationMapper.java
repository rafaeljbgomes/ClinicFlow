package com.clinicflow.clinical.application.mapping;

import com.clinicflow.clinical.application.results.CareGoalResult;
import com.clinicflow.clinical.application.results.CarePlanResult;
import com.clinicflow.clinical.application.results.ClinicalCaseResult;
import com.clinicflow.clinical.application.results.PracticeProfileResult;
import com.clinicflow.clinical.application.results.SessionRecordResult;
import com.clinicflow.clinical.domain.CareGoal;
import com.clinicflow.clinical.domain.CarePlan;
import com.clinicflow.clinical.domain.ClinicalCase;
import com.clinicflow.clinical.domain.PracticeProfile;
import com.clinicflow.clinical.domain.SessionRecord;
import org.mapstruct.Mapper;

@Mapper(config = MapperConfiguration.class)
public abstract class ClinicalApplicationMapper {
    public PracticeProfileResult toResult(PracticeProfile profile) {
        return new PracticeProfileResult(profile.psychologistId().value(), profile.professionalRegistration(),
                profile.timezone().getId(), profile.defaultSessionDurationMinutes(), profile.primaryLocation(),
                profile.telehealthEnabled(), profile.createdAt(), profile.updatedAt());
    }

    public ClinicalCaseResult toResult(ClinicalCase clinicalCase) {
        return new ClinicalCaseResult(clinicalCase.id().value(), clinicalCase.psychologistId().value(),
                clinicalCase.patientId().value(), clinicalCase.presentingConcern(), clinicalCase.status(),
                clinicalCase.openedAt(), clinicalCase.closedAt(), clinicalCase.createdAt(), clinicalCase.updatedAt());
    }

    public CarePlanResult toResult(CarePlan carePlan) {
        return new CarePlanResult(carePlan.id().value(), carePlan.clinicalCaseId().value(),
                carePlan.psychologistId().value(), carePlan.patientId().value(), carePlan.therapeuticFocus(),
                carePlan.plannedFrequency(), carePlan.reviewDate(),
                carePlan.goals().stream().map(this::toResult).toList(),
                carePlan.createdAt(), carePlan.updatedAt());
    }

    public CareGoalResult toResult(CareGoal goal) {
        return new CareGoalResult(goal.id().value(), goal.description(), goal.targetDate(),
                goal.status(), goal.progressPercentage());
    }

    public SessionRecordResult toResult(SessionRecord session) {
        return new SessionRecordResult(session.id().value(),
                session.clinicalCaseId().map(id -> id.value()).orElse(null),
                session.psychologistId().value(), session.patientId().value(),
                session.appointmentId().map(id -> id.value()).orElse(null), session.sessionDate(),
                session.modality(), session.durationMinutes(), session.attendanceStatus(), session.noteStatus(),
                session.summary(), session.focusAreas(), session.interventions(), session.homework(),
                session.nextSteps(), session.createdAt(), session.updatedAt());
    }
}
