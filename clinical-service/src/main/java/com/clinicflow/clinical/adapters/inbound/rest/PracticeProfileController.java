package com.clinicflow.clinical.adapters.inbound.rest;

import com.clinicflow.clinical.adapters.inbound.rest.dto.PracticeProfileRequest;
import com.clinicflow.clinical.adapters.inbound.rest.dto.PracticeProfileResponse;
import com.clinicflow.clinical.application.ClinicalService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/practice-profile")
@PreAuthorize("hasRole('PSYCHOLOGIST')")
public class PracticeProfileController {
    private final ClinicalService clinicalService;
    private final ClinicalRestMapper mapper;

    public PracticeProfileController(ClinicalService clinicalService, ClinicalRestMapper mapper) {
        this.clinicalService = clinicalService;
        this.mapper = mapper;
    }

    @GetMapping
    public PracticeProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(clinicalService.getPracticeProfile(
                mapper.toGetPracticeProfileQuery(userId(jwt))));
    }

    @PutMapping
    public PracticeProfileResponse update(@AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody PracticeProfileRequest request) {
        return mapper.toResponse(clinicalService.updatePracticeProfile(mapper.toCommand(userId(jwt), request)));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("userId"));
    }
}
