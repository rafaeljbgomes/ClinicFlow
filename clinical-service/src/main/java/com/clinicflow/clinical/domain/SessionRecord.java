package com.clinicflow.clinical.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class SessionRecord {
    private final SessionRecordId id;
    private ClinicalCaseId clinicalCaseId;
    private final PsychologistId psychologistId;
    private final PatientId patientId;
    private AppointmentId appointmentId;
    private Instant sessionDate;
    private SessionModality modality;
    private int durationMinutes;
    private AttendanceStatus attendanceStatus;
    private NoteStatus noteStatus;
    private String summary;
    private String focusAreas;
    private String interventions;
    private String homework;
    private String nextSteps;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant signedAt;
    private PsychologistId signedBy;
    // Assigned and incremented by persistence; null until the record is first saved.
    private final Long version;

    private SessionRecord(SessionRecordId id, ClinicalCaseId clinicalCaseId, PsychologistId psychologistId,
                          PatientId patientId, AppointmentId appointmentId, Instant sessionDate,
                          SessionModality modality, int durationMinutes, AttendanceStatus attendanceStatus,
                          NoteStatus noteStatus, String summary, String focusAreas, String interventions,
                          String homework, String nextSteps, Instant createdAt, Instant updatedAt,
                          Instant signedAt, PsychologistId signedBy, Long version) {
        if (id == null || psychologistId == null || patientId == null || sessionDate == null
                || modality == null || attendanceStatus == null || noteStatus == null
                || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("Session record required fields are missing");
        }
        requireDuration(durationMinutes);
        boolean signed = noteStatus == NoteStatus.SIGNED;
        if (signed != (signedAt != null) || signed != (signedBy != null)) {
            throw new IllegalArgumentException("Session record signature is inconsistent with its note status");
        }
        this.id = id;
        this.clinicalCaseId = clinicalCaseId;
        this.psychologistId = psychologistId;
        this.patientId = patientId;
        this.appointmentId = appointmentId;
        this.sessionDate = sessionDate;
        this.modality = modality;
        this.durationMinutes = durationMinutes;
        this.attendanceStatus = attendanceStatus;
        this.noteStatus = noteStatus;
        this.summary = TextFields.optional(summary, 2000, "Session summary is invalid");
        this.focusAreas = TextFields.optional(focusAreas, 1000, "Session focus areas are invalid");
        this.interventions = TextFields.optional(interventions, 1000, "Session interventions are invalid");
        this.homework = TextFields.optional(homework, 1000, "Session homework is invalid");
        this.nextSteps = TextFields.optional(nextSteps, 1000, "Session next steps are invalid");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.signedAt = signedAt;
        this.signedBy = signedBy;
        this.version = version;
    }

    public static SessionRecord manual(SessionRecordId id, ClinicalCase clinicalCase, AppointmentId appointmentId,
                                       Instant sessionDate, SessionModality modality, int durationMinutes,
                                       AttendanceStatus attendanceStatus, String summary, String focusAreas,
                                       String interventions, String homework, String nextSteps, Instant now) {
        NoteStatus noteStatus = summary == null || summary.isBlank() ? NoteStatus.PENDING_NOTE : NoteStatus.DRAFT;
        return new SessionRecord(id, clinicalCase.id(), clinicalCase.psychologistId(), clinicalCase.patientId(),
                appointmentId, sessionDate, modality, durationMinutes, attendanceStatus, noteStatus, summary,
                focusAreas, interventions, homework, nextSteps, now, now, null, null, null);
    }

    public static SessionRecord fromCompletedAppointment(SessionRecordId id, ClinicalCaseId clinicalCaseId,
                                                        PsychologistId psychologistId, PatientId patientId,
                                                        AppointmentId appointmentId, Instant sessionDate,
                                                        SessionModality modality, int durationMinutes, Instant now) {
        return new SessionRecord(id, clinicalCaseId, psychologistId, patientId, appointmentId, sessionDate,
                modality, durationMinutes, AttendanceStatus.ATTENDED, NoteStatus.PENDING_NOTE,
                null, null, null, null, null, now, now, null, null, null);
    }

    public static SessionRecord rehydrate(SessionRecordId id, ClinicalCaseId clinicalCaseId,
                                          PsychologistId psychologistId, PatientId patientId,
                                          AppointmentId appointmentId, Instant sessionDate, SessionModality modality,
                                          int durationMinutes, AttendanceStatus attendanceStatus, NoteStatus noteStatus,
                                          String summary, String focusAreas, String interventions, String homework,
                                          String nextSteps, Instant createdAt, Instant updatedAt,
                                          Instant signedAt, PsychologistId signedBy, Long version) {
        return new SessionRecord(id, clinicalCaseId, psychologistId, patientId, appointmentId, sessionDate,
                modality, durationMinutes, attendanceStatus, noteStatus, summary, focusAreas, interventions,
                homework, nextSteps, createdAt, updatedAt, signedAt, signedBy, version);
    }

    // Edits only move a note between PENDING_NOTE and DRAFT; signing is a separate, one-way action.
    public SessionRecord update(ClinicalCaseId clinicalCaseId, Instant sessionDate, SessionModality modality,
                                int durationMinutes, AttendanceStatus attendanceStatus, NoteStatus noteStatus,
                                String summary, String focusAreas, String interventions, String homework,
                                String nextSteps, Instant now) {
        requireUnsigned();
        if (now == null || now.isBefore(createdAt) || sessionDate == null || modality == null
                || attendanceStatus == null || noteStatus == null) {
            throw new IllegalArgumentException("Session record update fields are missing");
        }
        if (noteStatus == NoteStatus.SIGNED) {
            throw new InvalidNoteTransitionException();
        }
        requireDuration(durationMinutes);
        this.clinicalCaseId = clinicalCaseId;
        this.sessionDate = sessionDate;
        this.modality = modality;
        this.durationMinutes = durationMinutes;
        this.attendanceStatus = attendanceStatus;
        this.noteStatus = noteStatus;
        this.summary = TextFields.optional(summary, 2000, "Session summary is invalid");
        this.focusAreas = TextFields.optional(focusAreas, 1000, "Session focus areas are invalid");
        this.interventions = TextFields.optional(interventions, 1000, "Session interventions are invalid");
        this.homework = TextFields.optional(homework, 1000, "Session homework is invalid");
        this.nextSteps = TextFields.optional(nextSteps, 1000, "Session next steps are invalid");
        this.updatedAt = now;
        return this;
    }

    public SessionRecord sign(PsychologistId signer, Instant now) {
        requireUnsigned();
        if (signer == null || now == null || now.isBefore(createdAt)) {
            throw new IllegalArgumentException("Session record signature fields are missing");
        }
        if (!signer.equals(psychologistId)) {
            throw new IllegalArgumentException("Only the treating psychologist can sign a session note");
        }
        if (noteStatus != NoteStatus.DRAFT || summary == null) {
            throw new SessionRecordNotSignableException();
        }
        this.noteStatus = NoteStatus.SIGNED;
        this.signedAt = now;
        this.signedBy = signer;
        this.updatedAt = now;
        return this;
    }

    // Only the appointment's own psychologist and patient may hold its id on a session record (ADR-025).
    public boolean isRecordOf(PsychologistId appointmentPsychologistId, PatientId appointmentPatientId) {
        return psychologistId.equals(appointmentPsychologistId) && patientId.equals(appointmentPatientId);
    }

    // Releasing a link that was never valid is not a note edit: content, signature and updatedAt stay as they are,
    // so it is allowed on signed notes. The returned audit entry must be stored before the record is saved.
    public SessionRecordLinkConflict releaseAppointmentLink(SessionRecordLinkConflictId conflictId,
                                                            PsychologistId appointmentPsychologistId,
                                                            PatientId appointmentPatientId,
                                                            LinkConflictSource source, Instant now) {
        if (appointmentId == null) {
            throw new IllegalStateException("Session record has no appointment link to release");
        }
        if (isRecordOf(appointmentPsychologistId, appointmentPatientId)) {
            throw new IllegalStateException("Session record belongs to the appointment's owner");
        }
        SessionRecordLinkConflict conflict = new SessionRecordLinkConflict(conflictId, id, appointmentId,
                appointmentPsychologistId, appointmentPatientId, source, now);
        this.appointmentId = null;
        return conflict;
    }

    private void requireUnsigned() {
        if (noteStatus == NoteStatus.SIGNED) {
            throw new SignedSessionRecordException();
        }
    }

    private static void requireDuration(int value) {
        if (value < 0 || value > 480) {
            throw new IllegalArgumentException("Session duration is invalid");
        }
    }

    public SessionRecordId id() { return new SessionRecordId(id.value()); }
    public Optional<ClinicalCaseId> clinicalCaseId() {
        return Optional.ofNullable(clinicalCaseId).map(value -> new ClinicalCaseId(value.value()));
    }
    public PsychologistId psychologistId() { return new PsychologistId(psychologistId.value()); }
    public PatientId patientId() { return new PatientId(patientId.value()); }
    public Optional<AppointmentId> appointmentId() {
        return Optional.ofNullable(appointmentId).map(value -> new AppointmentId(value.value()));
    }
    public Instant sessionDate() { return sessionDate; }
    public SessionModality modality() { return modality; }
    public int durationMinutes() { return durationMinutes; }
    public AttendanceStatus attendanceStatus() { return attendanceStatus; }
    public NoteStatus noteStatus() { return noteStatus; }
    public String summary() { return summary; }
    public String focusAreas() { return focusAreas; }
    public String interventions() { return interventions; }
    public String homework() { return homework; }
    public String nextSteps() { return nextSteps; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Optional<Instant> signedAt() { return Optional.ofNullable(signedAt); }
    public Optional<PsychologistId> signedBy() {
        return Optional.ofNullable(signedBy).map(value -> new PsychologistId(value.value()));
    }
    public Optional<Long> version() { return Optional.ofNullable(version); }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof SessionRecord session && id.equals(session.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
