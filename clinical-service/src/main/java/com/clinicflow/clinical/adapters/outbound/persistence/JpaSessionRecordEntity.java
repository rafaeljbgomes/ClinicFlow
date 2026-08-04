package com.clinicflow.clinical.adapters.outbound.persistence;

import com.clinicflow.clinical.domain.AttendanceStatus;
import com.clinicflow.clinical.domain.NoteStatus;
import com.clinicflow.clinical.domain.SessionModality;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "session_records")
class JpaSessionRecordEntity {
    @Id private UUID id;
    @Column(name = "clinical_case_id") private UUID clinicalCaseId;
    @Column(name = "psychologist_id", nullable = false) private UUID psychologistId;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(name = "appointment_id", unique = true) private UUID appointmentId;
    @Column(name = "session_date", nullable = false) private Instant sessionDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private SessionModality modality;
    @Column(name = "duration_minutes", nullable = false) private int durationMinutes;
    @Enumerated(EnumType.STRING) @Column(name = "attendance_status", nullable = false, length = 32) private AttendanceStatus attendanceStatus;
    @Enumerated(EnumType.STRING) @Column(name = "note_status", nullable = false, length = 32) private NoteStatus noteStatus;
    @Column(length = 2000) private String summary;
    @Column(name = "focus_areas", length = 1000) private String focusAreas;
    @Column(length = 1000) private String interventions;
    @Column(length = 1000) private String homework;
    @Column(name = "next_steps", length = 1000) private String nextSteps;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected JpaSessionRecordEntity() {}

    JpaSessionRecordEntity(UUID id, UUID clinicalCaseId, UUID psychologistId, UUID patientId,
                           UUID appointmentId, Instant sessionDate, SessionModality modality, int durationMinutes,
                           AttendanceStatus attendanceStatus, NoteStatus noteStatus, String summary,
                           String focusAreas, String interventions, String homework, String nextSteps,
                           Instant createdAt, Instant updatedAt) {
        this.id = id; this.clinicalCaseId = clinicalCaseId; this.psychologistId = psychologistId;
        this.patientId = patientId; this.appointmentId = appointmentId; this.sessionDate = sessionDate;
        this.modality = modality; this.durationMinutes = durationMinutes;
        this.attendanceStatus = attendanceStatus; this.noteStatus = noteStatus; this.summary = summary;
        this.focusAreas = focusAreas; this.interventions = interventions; this.homework = homework;
        this.nextSteps = nextSteps; this.createdAt = createdAt; this.updatedAt = updatedAt;
    }

    UUID id() { return id; }
    UUID clinicalCaseId() { return clinicalCaseId; }
    UUID psychologistId() { return psychologistId; }
    UUID patientId() { return patientId; }
    UUID appointmentId() { return appointmentId; }
    Instant sessionDate() { return sessionDate; }
    SessionModality modality() { return modality; }
    int durationMinutes() { return durationMinutes; }
    AttendanceStatus attendanceStatus() { return attendanceStatus; }
    NoteStatus noteStatus() { return noteStatus; }
    String summary() { return summary; }
    String focusAreas() { return focusAreas; }
    String interventions() { return interventions; }
    String homework() { return homework; }
    String nextSteps() { return nextSteps; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
}
