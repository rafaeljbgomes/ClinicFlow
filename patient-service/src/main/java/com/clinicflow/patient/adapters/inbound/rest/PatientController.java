package com.clinicflow.patient.adapters.inbound.rest;

import com.clinicflow.patient.adapters.inbound.rest.dto.ChangePatientStatusRequest;
import com.clinicflow.patient.adapters.inbound.rest.dto.CreatePatientRequest;
import com.clinicflow.patient.adapters.inbound.rest.dto.PatientResponse;
import com.clinicflow.patient.adapters.inbound.rest.dto.UpdatePatientRequest;
import com.clinicflow.patient.application.PatientService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    @ResponseStatus(HttpStatus.CREATED)
    public PatientResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePatientRequest request) {
        PatientResponse patient = mapper.toResponse(patientService.create(mapper.toCommand(userId(jwt), request)));
        log.info("patient_created patientId={} psychologistId={}", patient.id(), patient.psychologistId());
        return patient;
    }

    @GetMapping
    public List<PatientResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return patientService.list(mapper.toListQuery(userId(jwt))).stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public PatientResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return mapper.toResponse(patientService.get(mapper.toGetQuery(userId(jwt), id)));
    }

    @PutMapping("/{id}")
    public PatientResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                  @Valid @RequestBody UpdatePatientRequest request) {
        return mapper.toResponse(patientService.update(mapper.toCommand(userId(jwt), id, request)));
    }

    @PatchMapping("/{id}/status")
    public PatientResponse changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                        @Valid @RequestBody ChangePatientStatusRequest request) {
        return mapper.toResponse(patientService.changeStatus(mapper.toCommand(userId(jwt), id, request)));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("userId"));
    }
}
