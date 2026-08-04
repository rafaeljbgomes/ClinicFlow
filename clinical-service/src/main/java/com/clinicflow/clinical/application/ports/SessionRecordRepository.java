package com.clinicflow.clinical.application.ports;

import com.clinicflow.clinical.domain.AppointmentId;
import com.clinicflow.clinical.domain.ClinicalCaseId;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PsychologistId;
import com.clinicflow.clinical.domain.SessionRecord;
import com.clinicflow.clinical.domain.SessionRecordId;

import java.util.List;
import java.util.Optional;

public interface SessionRecordRepository {
    Optional<SessionRecord> findById(SessionRecordId id);
    Optional<SessionRecord> findByAppointmentId(AppointmentId appointmentId);
    List<SessionRecord> findByClinicalCaseId(ClinicalCaseId clinicalCaseId);
    List<SessionRecord> findByPsychologistIdAndPatientId(PsychologistId psychologistId, PatientId patientId);
    void save(SessionRecord sessionRecord);
}
