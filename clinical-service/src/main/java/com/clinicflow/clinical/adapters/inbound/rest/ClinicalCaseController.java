package com.clinicflow.clinical.adapters.inbound.rest;

import com.clinicflow.clinical.adapters.inbound.rest.dto.CarePlanRequest;
import com.clinicflow.clinical.adapters.inbound.rest.dto.CarePlanResponse;
import com.clinicflow.clinical.adapters.inbound.rest.dto.ChangeClinicalCaseStatusRequest;
import com.clinicflow.clinical.adapters.inbound.rest.dto.ClinicalCaseResponse;
import com.clinicflow.clinical.adapters.inbound.rest.dto.CreateClinicalCaseRequest;
import com.clinicflow.clinical.adapters.inbound.rest.dto.CreateSessionRecordRequest;
import com.clinicflow.clinical.adapters.inbound.rest.dto.PatientClinicalHistoryResponse;
import com.clinicflow.clinical.adapters.inbound.rest.dto.SessionRecordResponse;
import com.clinicflow.clinical.adapters.inbound.rest.dto.UpdateSessionRecordRequest;
import com.clinicflow.clinical.application.ClinicalService;
import com.clinicflow.clinical.domain.ClinicalCaseStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@PreAuthorize("hasRole('PSYCHOLOGIST')")
public class ClinicalCaseController {
    private final ClinicalService clinicalService;
    private final ClinicalRestMapper mapper;

    public ClinicalCaseController(ClinicalService clinicalService, ClinicalRestMapper mapper) {
        this.clinicalService = clinicalService;
        this.mapper = mapper;
    }

    @PostMapping("/clinical-cases")
    @ResponseStatus(HttpStatus.CREATED)
    public ClinicalCaseResponse create(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody CreateClinicalCaseRequest request) {
        return mapper.toResponse(clinicalService.createClinicalCase(mapper.toCommand(userId(jwt), request)));
    }

    @GetMapping("/clinical-cases")
    public List<ClinicalCaseResponse> list(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(required = false) UUID patientId,
                                           @RequestParam(required = false) ClinicalCaseStatus status) {
        return clinicalService.listClinicalCases(mapper.toListQuery(userId(jwt), patientId, status))
                .stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/clinical-cases/{id}")
    public ClinicalCaseResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return mapper.toResponse(clinicalService.getClinicalCase(mapper.toGetCaseQuery(userId(jwt), id)));
    }

    @PatchMapping("/clinical-cases/{id}/status")
    public ClinicalCaseResponse changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                             @Valid @RequestBody ChangeClinicalCaseStatusRequest request) {
        return mapper.toResponse(clinicalService.changeClinicalCaseStatus(
                mapper.toCommand(userId(jwt), id, request)));
    }

    @GetMapping("/clinical-cases/{id}/care-plan")
    public CarePlanResponse getCarePlan(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return mapper.toResponse(clinicalService.getCarePlan(mapper.toGetCarePlanQuery(userId(jwt), id)));
    }

    @PutMapping("/clinical-cases/{id}/care-plan")
    public CarePlanResponse putCarePlan(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                        @Valid @RequestBody CarePlanRequest request) {
        return mapper.toResponse(clinicalService.putCarePlan(mapper.toCommand(userId(jwt), id, request)));
    }

    @GetMapping("/clinical-cases/{id}/session-records")
    public List<SessionRecordResponse> listSessions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return clinicalService.listSessionRecords(mapper.toListSessionsQuery(userId(jwt), id))
                .stream().map(mapper::toResponse).toList();
    }

    @PostMapping("/clinical-cases/{id}/session-records")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionRecordResponse createSession(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                               @Valid @RequestBody CreateSessionRecordRequest request) {
        return mapper.toResponse(clinicalService.createSessionRecord(mapper.toCommand(userId(jwt), id, request)));
    }

    @PatchMapping("/session-records/{id}")
    public SessionRecordResponse updateSession(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                               @Valid @RequestBody UpdateSessionRecordRequest request) {
        return mapper.toResponse(clinicalService.updateSessionRecord(mapper.toCommand(userId(jwt), id, request)));
    }

    @GetMapping("/patients/{patientId}/clinical-history")
    public PatientClinicalHistoryResponse history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID patientId) {
        return mapper.toResponse(clinicalService.getPatientClinicalHistory(mapper.toHistoryQuery(
                userId(jwt), patientId)));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("userId"));
    }
}
