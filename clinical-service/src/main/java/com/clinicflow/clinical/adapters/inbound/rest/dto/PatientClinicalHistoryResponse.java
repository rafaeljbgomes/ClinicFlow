package com.clinicflow.clinical.adapters.inbound.rest.dto;

import java.util.List;
import java.util.UUID;

public record PatientClinicalHistoryResponse(UUID patientId, List<ClinicalCaseResponse> cases,
                                             List<CarePlanResponse> carePlans,
                                             List<SessionRecordResponse> sessionRecords) {
}
