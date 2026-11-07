package com.clinicflow.patient.adapters.inbound.rest;

import com.clinicflow.patient.adapters.inbound.rest.dto.ChangePatientStatusRequest;
import com.clinicflow.patient.adapters.inbound.rest.dto.CreatePatientRequest;
import com.clinicflow.patient.adapters.inbound.rest.dto.PageResponse;
import com.clinicflow.patient.adapters.inbound.rest.dto.PatientResponse;
import com.clinicflow.patient.adapters.inbound.rest.dto.UpdatePatientRequest;
import com.clinicflow.patient.application.PatientService;
import com.clinicflow.patient.application.queries.PageQuery;
import com.clinicflow.patient.domain.ConsentStatus;
import com.clinicflow.patient.domain.PatientStatus;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/patients")
@PreAuthorize("hasRole('PSYCHOLOGIST')")
public class PatientController {
    private static final Logger log = LoggerFactory.getLogger(PatientController.class);
    private final PatientService patientService;
    private final PatientRestMapper mapper;

    public PatientController(PatientService patientService, PatientRestMapper mapper) {
        this.patientService = patientService;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<PatientResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody CreatePatientRequest request) {
        PatientResponse patient = mapper.toResponse(patientService.create(mapper.toCommand(userId(jwt), request)));
        log.info("patient_created patientId={} psychologistId={}", patient.id(), patient.psychologistId());
        return ResponseEntity.status(HttpStatus.CREATED).eTag(EntityTags.of(patient.version())).body(patient);
    }

    // Patients list by name (ADR-029). q searches names and email; status, consent and id are repeatable; id looks
    // up specific patients for screens that show names next to other services' records.
    @GetMapping
    public PageResponse<PatientResponse> list(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(required = false) Set<PatientStatus> status,
                                              @RequestParam(required = false) Set<ConsentStatus> consent,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
                                              @RequestParam(required = false) Set<UUID> id,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size) {
        return PageResponse.of(patientService.list(
                mapper.toListQuery(userId(jwt), q, status, consent, createdFrom, id, page, size)), mapper::toResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PatientResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return tagged(mapper.toResponse(patientService.get(mapper.toGetQuery(userId(jwt), id))));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PatientResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                  @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                                                  String ifMatch,
                                                  @Valid @RequestBody UpdatePatientRequest request) {
        long expectedVersion = EntityTags.requireIfMatch(ifMatch);
        return tagged(mapper.toResponse(patientService.update(
                mapper.toCommand(userId(jwt), id, request, expectedVersion))));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PatientResponse> changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                        @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
                                                        String ifMatch,
                                                        @Valid @RequestBody ChangePatientStatusRequest request) {
        long expectedVersion = EntityTags.requireIfMatch(ifMatch);
        return tagged(mapper.toResponse(patientService.changeStatus(
                mapper.toCommand(userId(jwt), id, request, expectedVersion))));
    }

    private static ResponseEntity<PatientResponse> tagged(PatientResponse patient) {
        return ResponseEntity.ok().eTag(EntityTags.of(patient.version())).body(patient);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
