package com.clinicflow.clinical.application;

import com.clinicflow.clinical.application.commands.CareGoalInput;
import com.clinicflow.clinical.application.commands.ChangeClinicalCaseStatusCommand;
import com.clinicflow.clinical.application.commands.CreateClinicalCaseCommand;
import com.clinicflow.clinical.application.commands.CreateSessionRecordCommand;
import com.clinicflow.clinical.application.commands.ProcessAppointmentCompletedCommand;
import com.clinicflow.clinical.application.commands.PutCarePlanCommand;
import com.clinicflow.clinical.application.commands.SignSessionRecordCommand;
import com.clinicflow.clinical.application.commands.UpdatePracticeProfileCommand;
import com.clinicflow.clinical.application.commands.UpdateSessionRecordCommand;
import com.clinicflow.clinical.application.exceptions.ActiveClinicalCaseExistsException;
import com.clinicflow.clinical.application.exceptions.AppointmentAlreadyRecordedException;
import com.clinicflow.clinical.application.exceptions.AppointmentNotFoundException;
import com.clinicflow.clinical.application.exceptions.AppointmentPatientMismatchException;
import com.clinicflow.clinical.application.exceptions.CarePlanNotFoundException;
import com.clinicflow.clinical.application.exceptions.ClinicalAccessDeniedException;
import com.clinicflow.clinical.application.exceptions.ClinicalCaseNotFoundException;
import com.clinicflow.clinical.application.exceptions.PatientNotFoundException;
import com.clinicflow.clinical.application.exceptions.SessionRecordNotFoundException;
import com.clinicflow.clinical.application.exceptions.StaleResourceVersionException;
import com.clinicflow.clinical.application.mapping.ClinicalApplicationMapper;
import com.clinicflow.clinical.application.ports.AppointmentDirectory;
import com.clinicflow.clinical.application.ports.CarePlanRepository;
import com.clinicflow.clinical.application.ports.ClinicalCaseRepository;
import com.clinicflow.clinical.application.ports.PatientDirectory;
import com.clinicflow.clinical.application.ports.PracticeProfileRepository;
import com.clinicflow.clinical.application.ports.SessionRecordLinkConflictRepository;
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
import com.clinicflow.clinical.domain.AppointmentId;
import com.clinicflow.clinical.domain.CareGoal;
import com.clinicflow.clinical.domain.CareGoalId;
import com.clinicflow.clinical.domain.CarePlan;
import com.clinicflow.clinical.domain.CarePlanId;
import com.clinicflow.clinical.domain.ClinicalCase;
import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.GoalStatus;
import com.clinicflow.clinical.domain.LinkConflictSource;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PracticeProfile;
import com.clinicflow.clinical.domain.PsychologistId;
import com.clinicflow.clinical.domain.SessionRecord;
import com.clinicflow.clinical.domain.SessionRecordId;
import com.clinicflow.clinical.domain.SessionRecordLinkConflict;
import com.clinicflow.clinical.domain.SessionRecordLinkConflictId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ClinicalService {
    private static final Logger log = LoggerFactory.getLogger(ClinicalService.class);
    private static final int FALLBACK_SESSION_DURATION_MINUTES = 50;

    private final PracticeProfileRepository practiceProfiles;
    private final ClinicalCaseRepository clinicalCases;
    private final CarePlanRepository carePlans;
    private final SessionRecordRepository sessionRecords;
    private final SessionRecordLinkConflictRepository linkConflicts;
    private final PatientDirectory patientDirectory;
    private final AppointmentDirectory appointmentDirectory;
    private final ClinicalApplicationMapper mapper;

    public ClinicalService(PracticeProfileRepository practiceProfiles, ClinicalCaseRepository clinicalCases,
                           CarePlanRepository carePlans, SessionRecordRepository sessionRecords,
                           SessionRecordLinkConflictRepository linkConflicts, PatientDirectory patientDirectory,
                           AppointmentDirectory appointmentDirectory, ClinicalApplicationMapper mapper) {
        this.practiceProfiles = practiceProfiles;
        this.clinicalCases = clinicalCases;
        this.carePlans = carePlans;
        this.sessionRecords = sessionRecords;
        this.linkConflicts = linkConflicts;
        this.patientDirectory = patientDirectory;
        this.appointmentDirectory = appointmentDirectory;
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
        Optional<PracticeProfile> existingProfile = practiceProfiles.findByPsychologistId(command.psychologistId());
        requireVersion(existingProfile.flatMap(PracticeProfile::version), command.expectedVersion());
        PracticeProfile profile = existingProfile
                .map(existing -> existing.update(command.professionalRegistration(), command.timezone(),
                        command.defaultSessionDurationMinutes(), command.primaryLocation(),
                        command.telehealthEnabled(), now))
                .orElseGet(() -> PracticeProfile.create(command.psychologistId(), command.professionalRegistration(),
                        command.timezone(), command.defaultSessionDurationMinutes(), command.primaryLocation(),
                        command.telehealthEnabled(), now));
        return mapper.toResult(practiceProfiles.save(profile));
    }

    @Transactional
    public ClinicalCaseResult createClinicalCase(CreateClinicalCaseCommand command) {
        // Unknown and foreign patients get the same answer so the endpoint is not an existence oracle.
        if (!patientDirectory.isOwnedBy(command.patientId(), command.psychologistId())) {
            throw new PatientNotFoundException();
        }
        if (clinicalCases.existsOpenByPsychologistIdAndPatientId(command.psychologistId(), command.patientId())) {
            throw new ActiveClinicalCaseExistsException();
        }
        ClinicalCase clinicalCase = ClinicalCase.create(new ClinicalCaseId(UUID.randomUUID()),
                command.psychologistId(), command.patientId(), command.presentingConcern(), Instant.now());
        return mapper.toResult(clinicalCases.save(clinicalCase));
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
        requireVersion(clinicalCase.version(), command.expectedVersion());
        clinicalCase.changeStatus(command.status(), Instant.now());
        return mapper.toResult(clinicalCases.save(clinicalCase));
    }

    @Transactional
    public CarePlanResult putCarePlan(PutCarePlanCommand command) {
        ClinicalCase clinicalCase = requireOwnedCase(command.psychologistId(), command.clinicalCaseId());
        Instant now = Instant.now();
        Optional<CarePlan> existingPlan = carePlans.findByClinicalCaseId(clinicalCase.id());
        requireVersion(existingPlan.flatMap(CarePlan::version), command.expectedVersion());
        Set<CareGoalId> ownedGoalIds = existingPlan
                .map(plan -> plan.goals().stream().map(CareGoal::id).collect(Collectors.toSet()))
                .orElseGet(Set::of);
        List<CareGoal> goals = command.goals().stream().map(input -> toGoal(input, ownedGoalIds)).toList();
        CarePlan carePlan = existingPlan
                .map(existing -> existing.replace(command.therapeuticFocus(), command.plannedFrequency(),
                        command.reviewDate(), goals, now))
                .orElseGet(() -> CarePlan.create(new CarePlanId(UUID.randomUUID()), clinicalCase,
                        command.therapeuticFocus(), command.plannedFrequency(), command.reviewDate(), goals, now));
        return mapper.toResult(carePlans.save(carePlan));
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
        Optional<SessionRecord> conflictingHolder = command.appointmentId() == null ? Optional.empty()
                : requireLinkableAppointment(command.psychologistId(), command.appointmentId(), clinicalCase);
        SessionRecord session = SessionRecord.manual(new SessionRecordId(UUID.randomUUID()), clinicalCase,
                command.appointmentId(), command.sessionDate(), command.modality(), command.durationMinutes(),
                command.attendanceStatus(), command.summary(), command.focusAreas(), command.interventions(),
                command.homework(), command.nextSteps(), Instant.now());
        conflictingHolder.ifPresent(holder -> releaseConflictingLink(holder, clinicalCase.psychologistId(),
                clinicalCase.patientId(), LinkConflictSource.MANUAL_LINK));
        return mapper.toResult(sessionRecords.save(session));
    }

    @Transactional(readOnly = true)
    public List<SessionRecordResult> listSessionRecords(ListSessionRecordsQuery query) {
        ClinicalCase clinicalCase = requireOwnedCase(query.psychologistId(), query.clinicalCaseId());
        return sessionRecords.findByClinicalCaseId(clinicalCase.id()).stream().map(mapper::toResult).toList();
    }

    @Transactional
    public SessionRecordResult updateSessionRecord(UpdateSessionRecordCommand command) {
        SessionRecord session = requireOwnedSession(command.psychologistId(), command.sessionRecordId());
        requireVersion(session.version(), command.expectedVersion());
        if (command.clinicalCaseId() != null) {
            ClinicalCase clinicalCase = requireOwnedCase(command.psychologistId(), command.clinicalCaseId());
            if (!clinicalCase.patientId().equals(session.patientId())) {
                throw new ClinicalAccessDeniedException();
            }
        }
        session.update(command.clinicalCaseId(), command.sessionDate(), command.modality(), command.durationMinutes(),
                command.attendanceStatus(), command.noteStatus(), command.summary(), command.focusAreas(),
                command.interventions(), command.homework(), command.nextSteps(), Instant.now());
        return mapper.toResult(sessionRecords.save(session));
    }

    @Transactional
    public SessionRecordResult signSessionRecord(SignSessionRecordCommand command) {
        SessionRecord session = requireOwnedSession(command.psychologistId(), command.sessionRecordId());
        requireVersion(session.version(), command.expectedVersion());
        session.sign(command.psychologistId(), Instant.now());
        return mapper.toResult(sessionRecords.save(session));
    }

    @Transactional
    public void processAppointmentCompleted(ProcessAppointmentCompletedCommand command) {
        Optional<SessionRecord> existing = sessionRecords.findByAppointmentId(command.appointmentId());
        if (existing.isPresent() && existing.get().isRecordOf(command.psychologistId(), command.patientId())) {
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
        existing.ifPresent(holder -> releaseConflictingLink(holder, command.psychologistId(), command.patientId(),
                LinkConflictSource.APPOINTMENT_COMPLETED));
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

    // Another psychologist's case or note answers exactly like a missing one, so ids cannot be probed.
    private ClinicalCase requireOwnedCase(PsychologistId psychologistId, ClinicalCaseId clinicalCaseId) {
        ClinicalCase clinicalCase = clinicalCases.findById(clinicalCaseId)
                .orElseThrow(ClinicalCaseNotFoundException::new);
        if (!clinicalCase.psychologistId().equals(psychologistId)) {
            log.warn("clinical_case_access_denied clinicalCaseId={} psychologistId={}", clinicalCaseId.value(),
                    psychologistId.value());
            throw new ClinicalCaseNotFoundException();
        }
        return clinicalCase;
    }

    private SessionRecord requireOwnedSession(PsychologistId psychologistId, SessionRecordId sessionRecordId) {
        SessionRecord session = sessionRecords.findById(sessionRecordId)
                .orElseThrow(SessionRecordNotFoundException::new);
        if (!session.psychologistId().equals(psychologistId)) {
            log.warn("session_record_access_denied sessionRecordId={} psychologistId={}", sessionRecordId.value(),
                    psychologistId.value());
            throw new SessionRecordNotFoundException();
        }
        return session;
    }

    // Runs after the ownership check, so another psychologist's record still looks missing, never stale.
    // An empty current version with a null expectation is a create that expected no record (If-None-Match: *).
    private static void requireVersion(Optional<Long> currentVersion, Long expectedVersion) {
        if (!Objects.equals(currentVersion.orElse(null), expectedVersion)) {
            throw new StaleResourceVersionException();
        }
    }

    // Ownership is checked before the local uniqueness check so a foreign appointment id reveals nothing.
    // Returns a record that holds the verified appointment without belonging to its owner, if there is one.
    private Optional<SessionRecord> requireLinkableAppointment(PsychologistId psychologistId,
                                                               AppointmentId appointmentId,
                                                               ClinicalCase clinicalCase) {
        PatientId appointmentPatient = appointmentDirectory.findPatientOfOwnedAppointment(appointmentId, psychologistId)
                .orElseThrow(AppointmentNotFoundException::new);
        if (!appointmentPatient.equals(clinicalCase.patientId())) {
            throw new AppointmentPatientMismatchException();
        }
        Optional<SessionRecord> holder = sessionRecords.findByAppointmentId(appointmentId);
        if (holder.isPresent() && holder.get().isRecordOf(psychologistId, appointmentPatient)) {
            throw new AppointmentAlreadyRecordedException();
        }
        return holder;
    }

    // Records linked before ADR-021 can hold another practice's appointment. Once appointment-service has named the
    // real owner, that link is released and audited so the owner's record can take it (ADR-025).
    private void releaseConflictingLink(SessionRecord holder, PsychologistId appointmentPsychologistId,
                                        PatientId appointmentPatientId, LinkConflictSource source) {
        SessionRecordLinkConflict conflict = holder.releaseAppointmentLink(
                new SessionRecordLinkConflictId(UUID.randomUUID()), appointmentPsychologistId, appointmentPatientId,
                source, Instant.now());
        // The audit entry goes first: the database lets a signed record drop a link only when it is recorded.
        linkConflicts.save(conflict);
        sessionRecords.save(holder);
        log.warn("session_record_appointment_conflict appointmentId={} sessionRecordId={} source={} "
                        + "resolution=link_released", conflict.appointmentId().value(), holder.id().value(), source);
    }

    // Goal IDs are only accepted when they already belong to this plan; new goals always get server IDs.
    private CareGoal toGoal(CareGoalInput input, Set<CareGoalId> ownedGoalIds) {
        CareGoalId goalId;
        if (input.id() == null) {
            goalId = new CareGoalId(UUID.randomUUID());
        } else {
            goalId = new CareGoalId(input.id());
            if (!ownedGoalIds.contains(goalId)) {
                throw new ClinicalAccessDeniedException();
            }
        }
        return new CareGoal(goalId, input.description(), input.targetDate(),
                input.status() == null ? GoalStatus.NOT_STARTED : input.status(),
                input.progressPercentage());
    }
}
