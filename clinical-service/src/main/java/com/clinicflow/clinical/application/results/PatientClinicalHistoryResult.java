package com.clinicflow.clinical.application.results;

import java.util.List;
import java.util.UUID;

public record PatientClinicalHistoryResult(UUID patientId, List<ClinicalCaseResult> cases,
                                           List<CarePlanResult> carePlans,
                                           List<SessionRecordResult> sessionRecords) {
}
