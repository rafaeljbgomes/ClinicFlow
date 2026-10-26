package com.clinicflow.clinical.adapters.inbound.rest;

import com.clinicflow.clinical.adapters.inbound.rest.dto.CarePlanRequest;
import com.clinicflow.clinical.adapters.inbound.rest.dto.CarePlanResponse;
import com.clinicflow.clinical.adapters.inbound.rest.dto.ChangeClinicalCaseStatusRequest;
import com.clinicflow.clinical.adapters.inbound.rest.dto.ClinicalCaseResponse;
import com.clinicflow.clinical.adapters.inbound.rest.dto.CreateClinicalCaseRequest;
import com.clinicflow.clinical.adapters.inbound.rest.dto.CreateSessionRecordRequest;
import com.clinicflow.clinical.adapters.inbound.rest.dto.PageResponse;
import com.clinicflow.clinical.adapters.inbound.rest.dto.PatientClinicalHistoryResponse;
import com.clinicflow.clinical.adapters.inbound.rest.dto.SessionRecordResponse;
import com.clinicflow.clinical.adapters.inbound.rest.dto.UpdateSessionRecordRequest;
import com.clinicflow.clinical.application.ClinicalService;
import com.clinicflow.clinical.application.queries.PageQuery;
import com.clinicflow.clinical.domain.ClinicalCaseStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
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
    public ResponseEntity<ClinicalCaseResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody CreateClinicalCaseRequest request) {
        ClinicalCaseResponse clinicalCase = mapper.toResponse(
                clinicalService.createClinicalCase(mapper.toCommand(userId(jwt), request)));
        return ResponseEntity.status(HttpStatus.CREATED).eTag(EntityTags.of(clinicalCase.version()))
                .body(clinicalCase);
    }

    // Cases list the most recently opened first; status is repeatable.
    @GetMapping("/clinical-cases")
    public PageResponse<ClinicalCaseResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestParam(required = false) UUID patientId,
                                                   @RequestParam(required = false) Set<ClinicalCaseStatus> status,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size) {
        return PageResponse.of(clinicalService.listClinicalCases(
                mapper.toListQuery(userId(jwt), patientId, status, page, size)), mapper::toResponse);
    }

    @GetMapping("/clinical-cases/{id}")
    public ResponseEntity<ClinicalCaseResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        ClinicalCaseResponse clinicalCase = mapper.toResponse(
                clinicalService.getClinicalCase(mapper.toGetCaseQuery(userId(jwt), id)));
        return ResponseEntity.ok().eTag(EntityTags.of(clinicalCase.version())).body(clinicalCase);
    }

    @PatchMapping("/clinical-cases/{id}/status")
    public ResponseEntity<ClinicalCaseResponse> changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                       @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                                                       String ifMatch,
                                                       @Valid @RequestBody ChangeClinicalCaseStatusRequest request) {
        long expectedVersion = EntityTags.requireIfMatch(ifMatch);
        ClinicalCaseResponse clinicalCase = mapper.toResponse(clinicalService.changeClinicalCaseStatus(
                mapper.toCommand(userId(jwt), id, request, expectedVersion)));
        return ResponseEntity.ok().eTag(EntityTags.of(clinicalCase.version())).body(clinicalCase);
    }

    @GetMapping("/clinical-cases/{id}/care-plan")
    public ResponseEntity<CarePlanResponse> getCarePlan(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        CarePlanResponse carePlan = mapper.toResponse(
                clinicalService.getCarePlan(mapper.toGetCarePlanQuery(userId(jwt), id)));
        return ResponseEntity.ok().eTag(EntityTags.of(carePlan.version())).body(carePlan);
    }

    // The first PUT creates the plan (If-None-Match: *); later ones replace the version named in If-Match.
    @PutMapping("/clinical-cases/{id}/care-plan")
    public ResponseEntity<CarePlanResponse> putCarePlan(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                       @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                                                       String ifMatch,
                                                       @RequestHeader(name = HttpHeaders.IF_NONE_MATCH,
                                                               required = false) String ifNoneMatch,
                                                       @Valid @RequestBody CarePlanRequest request) {
        Long expectedVersion = EntityTags.requireUpsertPrecondition(ifMatch, ifNoneMatch);
        CarePlanResponse carePlan = mapper.toResponse(clinicalService.putCarePlan(
                mapper.toCommand(userId(jwt), id, request, expectedVersion)));
        return ResponseEntity.ok().eTag(EntityTags.of(carePlan.version())).body(carePlan);
    }

    // Session records list the latest session first.
    @GetMapping("/clinical-cases/{id}/session-records")
    public PageResponse<SessionRecordResponse> listSessions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE)
                                                            int size) {
        return PageResponse.of(clinicalService.listSessionRecords(
                mapper.toListSessionsQuery(userId(jwt), id, page, size)), mapper::toResponse);
    }

    @PostMapping("/clinical-cases/{id}/session-records")
    public ResponseEntity<SessionRecordResponse> createSession(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                               @Valid @RequestBody CreateSessionRecordRequest request) {
        SessionRecordResponse session = mapper.toResponse(
                clinicalService.createSessionRecord(mapper.toCommand(userId(jwt), id, request)));
        return ResponseEntity.status(HttpStatus.CREATED).eTag(EntityTags.of(session.version())).body(session);
    }

    @PatchMapping("/session-records/{id}")
    public ResponseEntity<SessionRecordResponse> updateSession(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                       @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                                                       String ifMatch,
                                                       @Valid @RequestBody UpdateSessionRecordRequest request) {
        long expectedVersion = EntityTags.requireIfMatch(ifMatch);
        return tagged(mapper.toResponse(clinicalService.updateSessionRecord(
                mapper.toCommand(userId(jwt), id, request, expectedVersion))));
    }

    // If-Match names the revision the psychologist reviewed; a newer revision is never signed in its place.
    @PostMapping("/session-records/{id}/sign")
    public ResponseEntity<SessionRecordResponse> signSession(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                             @RequestHeader(name = HttpHeaders.IF_MATCH,
                                                                     required = false) String ifMatch) {
        long expectedVersion = EntityTags.requireIfMatch(ifMatch);
        return tagged(mapper.toResponse(clinicalService.signSessionRecord(
                mapper.toSignCommand(userId(jwt), id, expectedVersion))));
    }

    private static ResponseEntity<SessionRecordResponse> tagged(SessionRecordResponse session) {
        return ResponseEntity.ok().eTag(EntityTags.of(session.version())).body(session);
    }

    @GetMapping("/patients/{patientId}/clinical-history")
    public PatientClinicalHistoryResponse history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID patientId) {
        return mapper.toResponse(clinicalService.getPatientClinicalHistory(mapper.toHistoryQuery(
                userId(jwt), patientId)));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
