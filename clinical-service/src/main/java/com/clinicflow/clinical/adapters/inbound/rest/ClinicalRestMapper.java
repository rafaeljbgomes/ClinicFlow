package com.clinicflow.clinical.adapters.inbound.rest;

import com.clinicflow.clinical.adapters.inbound.rest.dto.CareGoalRequest;
import com.clinicflow.clinical.adapters.inbound.rest.dto.CareGoalResponse;
import com.clinicflow.clinical.adapters.inbound.rest.dto.CarePlanRequest;
import com.clinicflow.clinical.adapters.inbound.rest.dto.CarePlanResponse;
import com.clinicflow.clinical.adapters.inbound.rest.dto.ChangeClinicalCaseStatusRequest;
import com.clinicflow.clinical.adapters.inbound.rest.dto.ClinicalCaseResponse;
import com.clinicflow.clinical.adapters.inbound.rest.dto.CreateClinicalCaseRequest;
import com.clinicflow.clinical.adapters.inbound.rest.dto.CreateSessionRecordRequest;
import com.clinicflow.clinical.adapters.inbound.rest.dto.PatientClinicalHistoryResponse;
import com.clinicflow.clinical.adapters.inbound.rest.dto.PracticeProfileRequest;
import com.clinicflow.clinical.adapters.inbound.rest.dto.PracticeProfileResponse;
import com.clinicflow.clinical.adapters.inbound.rest.dto.SessionRecordResponse;
import com.clinicflow.clinical.adapters.inbound.rest.dto.UpdateSessionRecordRequest;
import com.clinicflow.clinical.application.commands.CareGoalInput;
import com.clinicflow.clinical.application.commands.ChangeClinicalCaseStatusCommand;
import com.clinicflow.clinical.application.commands.CreateClinicalCaseCommand;
import com.clinicflow.clinical.application.commands.CreateSessionRecordCommand;
import com.clinicflow.clinical.application.commands.PutCarePlanCommand;
import com.clinicflow.clinical.application.commands.UpdatePracticeProfileCommand;
import com.clinicflow.clinical.application.commands.UpdateSessionRecordCommand;
import com.clinicflow.clinical.application.mapping.MapperConfiguration;
import com.clinicflow.clinical.application.queries.GetCarePlanQuery;
import com.clinicflow.clinical.application.queries.GetClinicalCaseQuery;
import com.clinicflow.clinical.application.queries.GetPatientClinicalHistoryQuery;
import com.clinicflow.clinical.application.queries.GetPracticeProfileQuery;
import com.clinicflow.clinical.application.queries.ListClinicalCasesQuery;
import com.clinicflow.clinical.application.queries.ListSessionRecordsQuery;
import com.clinicflow.clinical.application.results.CareGoalResult;
import com.clinicflow.clinical.application.results.CarePlanResult;
import com.clinicflow.clinical.application.results.ClinicalCaseResult;
import com.clinicflow.clinical.application.results.PatientClinicalHistoryResult;
import com.clinicflow.clinical.application.results.PracticeProfileResult;
import com.clinicflow.clinical.application.results.SessionRecordResult;
import com.clinicflow.clinical.domain.AppointmentId;
import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.ClinicalCaseStatus;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PsychologistId;
import com.clinicflow.clinical.domain.SessionRecordId;
import org.mapstruct.Mapper;

import java.time.ZoneId;
import java.time.DateTimeException;
import java.util.UUID;

@Mapper(config = MapperConfiguration.class)
public abstract class ClinicalRestMapper {
    public GetPracticeProfileQuery toGetPracticeProfileQuery(UUID psychologistId) {
        return new GetPracticeProfileQuery(new PsychologistId(psychologistId));
    }

    public UpdatePracticeProfileCommand toCommand(UUID psychologistId, PracticeProfileRequest request) {
        return new UpdatePracticeProfileCommand(new PsychologistId(psychologistId),
                request.professionalRegistration(), zoneId(request.timezone()),
                request.defaultSessionDurationMinutes(), request.primaryLocation(), request.telehealthEnabled());
    }

    public CreateClinicalCaseCommand toCommand(UUID psychologistId, CreateClinicalCaseRequest request) {
        return new CreateClinicalCaseCommand(new PsychologistId(psychologistId),
                new PatientId(request.patientId()), request.presentingConcern());
    }

    public ListClinicalCasesQuery toListQuery(UUID psychologistId, UUID patientId, ClinicalCaseStatus status) {
        return new ListClinicalCasesQuery(new PsychologistId(psychologistId),
                patientId == null ? null : new PatientId(patientId), status);
    }

    public GetClinicalCaseQuery toGetCaseQuery(UUID psychologistId, UUID clinicalCaseId) {
        return new GetClinicalCaseQuery(new PsychologistId(psychologistId), new ClinicalCaseId(clinicalCaseId));
    }

    public ChangeClinicalCaseStatusCommand toCommand(UUID psychologistId, UUID clinicalCaseId,
                                                     ChangeClinicalCaseStatusRequest request) {
        return new ChangeClinicalCaseStatusCommand(new PsychologistId(psychologistId),
                new ClinicalCaseId(clinicalCaseId), request.status());
    }

    public PutCarePlanCommand toCommand(UUID psychologistId, UUID clinicalCaseId, CarePlanRequest request) {
        return new PutCarePlanCommand(new PsychologistId(psychologistId), new ClinicalCaseId(clinicalCaseId),
                request.therapeuticFocus(), request.plannedFrequency(), request.reviewDate(),
                request.goals().stream().map(this::toInput).toList());
    }

    public GetCarePlanQuery toGetCarePlanQuery(UUID psychologistId, UUID clinicalCaseId) {
        return new GetCarePlanQuery(new PsychologistId(psychologistId), new ClinicalCaseId(clinicalCaseId));
    }

    public ListSessionRecordsQuery toListSessionsQuery(UUID psychologistId, UUID clinicalCaseId) {
        return new ListSessionRecordsQuery(new PsychologistId(psychologistId), new ClinicalCaseId(clinicalCaseId));
    }

    public CreateSessionRecordCommand toCommand(UUID psychologistId, UUID clinicalCaseId,
                                                CreateSessionRecordRequest request) {
        return new CreateSessionRecordCommand(new PsychologistId(psychologistId),
                new ClinicalCaseId(clinicalCaseId),
                request.appointmentId() == null ? null : new AppointmentId(request.appointmentId()),
                request.sessionDate(), request.modality(), request.durationMinutes(), request.attendanceStatus(),
                request.summary(), request.focusAreas(), request.interventions(), request.homework(),
                request.nextSteps());
    }

    public UpdateSessionRecordCommand toCommand(UUID psychologistId, UUID sessionRecordId,
                                                UpdateSessionRecordRequest request) {
        return new UpdateSessionRecordCommand(new PsychologistId(psychologistId),
                new SessionRecordId(sessionRecordId),
                request.clinicalCaseId() == null ? null : new ClinicalCaseId(request.clinicalCaseId()),
                request.sessionDate(), request.modality(), request.durationMinutes(), request.attendanceStatus(),
                request.noteStatus(), request.summary(), request.focusAreas(), request.interventions(),
                request.homework(), request.nextSteps());
    }

    public GetPatientClinicalHistoryQuery toHistoryQuery(UUID psychologistId, UUID patientId) {
        return new GetPatientClinicalHistoryQuery(new PsychologistId(psychologistId), new PatientId(patientId));
    }

    public abstract PracticeProfileResponse toResponse(PracticeProfileResult result);
    public abstract ClinicalCaseResponse toResponse(ClinicalCaseResult result);
    public abstract CarePlanResponse toResponse(CarePlanResult result);
    public abstract CareGoalResponse toResponse(CareGoalResult result);
    public abstract SessionRecordResponse toResponse(SessionRecordResult result);
    public abstract PatientClinicalHistoryResponse toResponse(PatientClinicalHistoryResult result);

    private CareGoalInput toInput(CareGoalRequest request) {
        return new CareGoalInput(request.id(), request.description(), request.targetDate(),
                request.status(), request.progressPercentage());
    }

    private ZoneId zoneId(String value) {
        try {
            return ZoneId.of(value);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Timezone is invalid", ex);
        }
    }
}
