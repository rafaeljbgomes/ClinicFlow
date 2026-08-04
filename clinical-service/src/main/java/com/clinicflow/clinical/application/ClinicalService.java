package com.clinicflow.clinical.application;

import com.clinicflow.clinical.application.commands.CareGoalInput;
import com.clinicflow.clinical.application.commands.ChangeClinicalCaseStatusCommand;
import com.clinicflow.clinical.application.commands.CreateClinicalCaseCommand;
import com.clinicflow.clinical.application.commands.CreateSessionRecordCommand;
import com.clinicflow.clinical.application.commands.ProcessAppointmentCompletedCommand;
import com.clinicflow.clinical.application.commands.PutCarePlanCommand;
import com.clinicflow.clinical.application.commands.UpdatePracticeProfileCommand;
import com.clinicflow.clinical.application.commands.UpdateSessionRecordCommand;
import com.clinicflow.clinical.application.exceptions.ActiveClinicalCaseExistsException;
import com.clinicflow.clinical.application.exceptions.CarePlanNotFoundException;
import com.clinicflow.clinical.application.exceptions.ClinicalAccessDeniedException;
import com.clinicflow.clinical.application.exceptions.ClinicalCaseNotFoundException;
import com.clinicflow.clinical.application.exceptions.SessionRecordNotFoundException;
import com.clinicflow.clinical.application.mapping.ClinicalApplicationMapper;
import com.clinicflow.clinical.application.ports.CarePlanRepository;
import com.clinicflow.clinical.application.ports.ClinicalCaseRepository;
import com.clinicflow.clinical.application.ports.PracticeProfileRepository;
import com.clinicflow.clinical.application.ports.SessionRecordRepository;
import com.clinicflow.clinical.application.queries.GetCarePlanQuery;
import com.clinicflow.clinical.application.queries.GetClinicalCaseQuery;
import com.clinicflow.clinical.application.queries.GetPatientClinicalHistoryQuery;
import com.clinicflow.clinical.application.queries.GetPracticeProfileQuery;
import com.clinicflow.clinical.application.queries.ListClinicalCasesQuery;
import com.clinicflow.clinical.application.queries.ListSessionRecordsQuery;
import com.clinicflow.clinical.application.results.CarePlanResult;
import com.clinicflow.clinical.application.results.ClinicalCaseResult;
import com.clinicflow.clinical.application.results.PatientClinicalHistoryResult;
import com.clinicflow.clinical.application.results.PracticeProfileResult;
import com.clinicflow.clinical.application.results.SessionRecordResult;
import com.clinicflow.clinical.domain.CareGoal;
import com.clinicflow.clinical.domain.CareGoalId;
import com.clinicflow.clinical.domain.CarePlan;
import com.clinicflow.clinical.domain.CarePlanId;
import com.clinicflow.clinical.domain.ClinicalCase;
import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.GoalStatus;
import com.clinicflow.clinical.domain.PracticeProfile;
import com.clinicflow.clinical.domain.PsychologistId;
import com.clinicflow.clinical.domain.SessionRecord;
import com.clinicflow.clinical.domain.SessionRecordId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class ClinicalService {
    private static final int FALLBACK_SESSION_DURATION_MINUTES = 50;

    private final PracticeProfileRepository practiceProfiles;
    private final ClinicalCaseRepository clinicalCases;
    private final CarePlanRepository carePlans;
    private final SessionRecordRepository sessionRecords;
    private final ClinicalApplicationMapper mapper;

    public ClinicalService(PracticeProfileRepository practiceProfiles, ClinicalCaseRepository clinicalCases,
                           CarePlanRepository carePlans, SessionRecordRepository sessionRecords,
                           ClinicalApplicationMapper mapper) {
        this.practiceProfiles = practiceProfiles;
        this.clinicalCases = clinicalCases;
        this.carePlans = carePlans;
        this.sessionRecords = sessionRecords;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PracticeProfileResult getPracticeProfile(GetPracticeProfileQuery query) {
        return mapper.toResult(practiceProfiles.findByPsychologistId(query.psychologistId())
                .orElseGet(() -> PracticeProfile.create(query.psychologistId(), null,
                        ZoneId.of("UTC"), FALLBACK_SESSION_DURATION_MINUTES, null, false, Instant.now())));
    }

    @Transactional
    public PracticeProfileResult updatePracticeProfile(UpdatePracticeProfileCommand command) {
        Instant now = Instant.now();
        PracticeProfile profile = practiceProfiles.findByPsychologistId(command.psychologistId())
                .map(existing -> existing.update(command.professionalRegistration(), command.timezone(),
                        command.defaultSessionDurationMinutes(), command.primaryLocation(),
                        command.telehealthEnabled(), now))
                .orElseGet(() -> PracticeProfile.create(command.psychologistId(), command.professionalRegistration(),
                        command.timezone(), command.defaultSessionDurationMinutes(), command.primaryLocation(),
                        command.telehealthEnabled(), now));
        practiceProfiles.save(profile);
        return mapper.toResult(profile);
    }

    @Transactional
    public ClinicalCaseResult createClinicalCase(CreateClinicalCaseCommand command) {
        if (clinicalCases.existsOpenByPsychologistIdAndPatientId(command.psychologistId(), command.patientId())) {
            throw new ActiveClinicalCaseExistsException();
        }
        ClinicalCase clinicalCase = ClinicalCase.create(new ClinicalCaseId(UUID.randomUUID()),
                command.psychologistId(), command.patientId(), command.presentingConcern(), Instant.now());
        clinicalCases.save(clinicalCase);
        return mapper.toResult(clinicalCase);
    }

    @Transactional(readOnly = true)
    public List<ClinicalCaseResult> listClinicalCases(ListClinicalCasesQuery query) {
        List<ClinicalCase> cases;
        if (query.patientId() != null) {
            cases = clinicalCases.findByPsychologistIdAndPatientId(query.psychologistId(), query.patientId());
            if (query.status() != null) {
                cases = cases.stream().filter(clinicalCase -> clinicalCase.status() == query.status()).toList();
            }
        } else if (query.status() != null) {
            cases = clinicalCases.findByPsychologistIdAndStatus(query.psychologistId(), query.status());
        } else {
            cases = clinicalCases.findByPsychologistId(query.psychologistId());
        }
        return cases.stream().map(mapper::toResult).toList();
    }

    @Transactional(readOnly = true)
    public ClinicalCaseResult getClinicalCase(GetClinicalCaseQuery query) {
        return mapper.toResult(requireOwnedCase(query.psychologistId(), query.clinicalCaseId()));
    }

    @Transactional
    public ClinicalCaseResult changeClinicalCaseStatus(ChangeClinicalCaseStatusCommand command) {
        ClinicalCase clinicalCase = requireOwnedCase(command.psychologistId(), command.clinicalCaseId());
        clinicalCase.changeStatus(command.status(), Instant.now());
        clinicalCases.save(clinicalCase);
        return mapper.toResult(clinicalCase);
    }

    @Transactional
    public CarePlanResult putCarePlan(PutCarePlanCommand command) {
        ClinicalCase clinicalCase = requireOwnedCase(command.psychologistId(), command.clinicalCaseId());
        Instant now = Instant.now();
        List<CareGoal> goals = command.goals().stream().map(this::toGoal).toList();
        CarePlan carePlan = carePlans.findByClinicalCaseId(clinicalCase.id())
                .map(existing -> existing.replace(command.therapeuticFocus(), command.plannedFrequency(),
                        command.reviewDate(), goals, now))
                .orElseGet(() -> CarePlan.create(new CarePlanId(UUID.randomUUID()), clinicalCase,
                        command.therapeuticFocus(), command.plannedFrequency(), command.reviewDate(), goals, now));
        carePlans.save(carePlan);
        return mapper.toResult(carePlan);
    }

    @Transactional(readOnly = true)
    public CarePlanResult getCarePlan(GetCarePlanQuery query) {
        ClinicalCase clinicalCase = requireOwnedCase(query.psychologistId(), query.clinicalCaseId());
        return mapper.toResult(carePlans.findByClinicalCaseId(clinicalCase.id())
                .orElseThrow(CarePlanNotFoundException::new));
    }

    @Transactional
    public SessionRecordResult createSessionRecord(CreateSessionRecordCommand command) {
        ClinicalCase clinicalCase = requireOwnedCase(command.psychologistId(), command.clinicalCaseId());
        SessionRecord session = SessionRecord.manual(new SessionRecordId(UUID.randomUUID()), clinicalCase,
                command.appointmentId(), command.sessionDate(), command.modality(), command.durationMinutes(),
                command.attendanceStatus(), command.summary(), command.focusAreas(), command.interventions(),
                command.homework(), command.nextSteps(), Instant.now());
        sessionRecords.save(session);
        return mapper.toResult(session);
    }

    @Transactional(readOnly = true)
    public List<SessionRecordResult> listSessionRecords(ListSessionRecordsQuery query) {
        ClinicalCase clinicalCase = requireOwnedCase(query.psychologistId(), query.clinicalCaseId());
        return sessionRecords.findByClinicalCaseId(clinicalCase.id()).stream().map(mapper::toResult).toList();
    }

    @Transactional
    public SessionRecordResult updateSessionRecord(UpdateSessionRecordCommand command) {
        SessionRecord session = sessionRecords.findById(command.sessionRecordId())
                .orElseThrow(SessionRecordNotFoundException::new);
        ensureOwned(command.psychologistId(), session.psychologistId());
        if (command.clinicalCaseId() != null) {
            ClinicalCase clinicalCase = requireOwnedCase(command.psychologistId(), command.clinicalCaseId());
            if (!clinicalCase.patientId().equals(session.patientId())) {
                throw new ClinicalAccessDeniedException();
            }
        }
        session.update(command.clinicalCaseId(), command.sessionDate(), command.modality(), command.durationMinutes(),
                command.attendanceStatus(), command.noteStatus(), command.summary(), command.focusAreas(),
                command.interventions(), command.homework(), command.nextSteps(), Instant.now());
        sessionRecords.save(session);
        return mapper.toResult(session);
    }

    @Transactional
    public void processAppointmentCompleted(ProcessAppointmentCompletedCommand command) {
        if (sessionRecords.findByAppointmentId(command.appointmentId()).isPresent()) {
            return;
        }
        ClinicalCaseId caseId = clinicalCases.findOpenByPsychologistIdAndPatientId(
                command.psychologistId(), command.patientId()).map(ClinicalCase::id).orElse(null);
        int duration = practiceProfiles.findByPsychologistId(command.psychologistId())
                .map(PracticeProfile::defaultSessionDurationMinutes)
                .orElse(FALLBACK_SESSION_DURATION_MINUTES);
        SessionRecord session = SessionRecord.fromCompletedAppointment(new SessionRecordId(UUID.randomUUID()),
                caseId, command.psychologistId(), command.patientId(), command.appointmentId(),
                command.appointmentDate(), command.modality(), duration, Instant.now());
        sessionRecords.save(session);
    }

    @Transactional(readOnly = true)
    public PatientClinicalHistoryResult getPatientClinicalHistory(GetPatientClinicalHistoryQuery query) {
        List<ClinicalCase> cases = clinicalCases.findByPsychologistIdAndPatientId(
                query.psychologistId(), query.patientId());
        List<CarePlanResult> plans = cases.stream()
                .flatMap(clinicalCase -> carePlans.findByClinicalCaseId(clinicalCase.id()).stream())
                .map(mapper::toResult)
                .toList();
        List<SessionRecordResult> sessions = sessionRecords.findByPsychologistIdAndPatientId(
                query.psychologistId(), query.patientId()).stream().map(mapper::toResult).toList();
        return new PatientClinicalHistoryResult(query.patientId().value(),
                cases.stream().map(mapper::toResult).toList(), plans, sessions);
    }

    private ClinicalCase requireOwnedCase(PsychologistId psychologistId, ClinicalCaseId clinicalCaseId) {
        ClinicalCase clinicalCase = clinicalCases.findById(clinicalCaseId)
                .orElseThrow(ClinicalCaseNotFoundException::new);
        ensureOwned(psychologistId, clinicalCase.psychologistId());
        return clinicalCase;
    }

    private void ensureOwned(PsychologistId expected, PsychologistId actual) {
        if (!expected.equals(actual)) {
            throw new ClinicalAccessDeniedException();
        }
    }

    private CareGoal toGoal(CareGoalInput input) {
        return new CareGoal(new CareGoalId(input.id() == null ? UUID.randomUUID() : input.id()),
                input.description(), input.targetDate(),
                input.status() == null ? GoalStatus.NOT_STARTED : input.status(),
                input.progressPercentage());
    }
}
