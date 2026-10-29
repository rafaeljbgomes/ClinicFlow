package com.clinicflow.clinical.adapters.inbound.rest;

import com.clinicflow.clinical.adapters.inbound.rest.dto.PracticeProfileRequest;
import com.clinicflow.clinical.adapters.inbound.rest.dto.PracticeProfileResponse;
import com.clinicflow.clinical.application.ClinicalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

    // Before the first save this returns defaults with no version and no entity tag.
    @GetMapping
    public ResponseEntity<PracticeProfileResponse> get(@AuthenticationPrincipal Jwt jwt) {
        PracticeProfileResponse profile = mapper.toResponse(clinicalService.getPracticeProfile(
                mapper.toGetPracticeProfileQuery(userId(jwt))));
        return profile.version() == null ? ResponseEntity.ok(profile)
                : ResponseEntity.ok().eTag(EntityTags.of(profile.version())).body(profile);
    }

    // The first PUT creates the profile (If-None-Match: *); later ones replace the version named in If-Match.
    @PutMapping
    public ResponseEntity<PracticeProfileResponse> update(@AuthenticationPrincipal Jwt jwt,
                                                          @RequestHeader(name = HttpHeaders.IF_MATCH,
                                                                  required = false) String ifMatch,
                                                          @RequestHeader(name = HttpHeaders.IF_NONE_MATCH,
                                                                  required = false) String ifNoneMatch,
                                                          @Valid @RequestBody PracticeProfileRequest request) {
        Long expectedVersion = EntityTags.requireUpsertPrecondition(ifMatch, ifNoneMatch);
        PracticeProfileResponse profile = mapper.toResponse(clinicalService.updatePracticeProfile(
                mapper.toCommand(userId(jwt), request, expectedVersion)));
        return ResponseEntity.ok().eTag(EntityTags.of(profile.version())).body(profile);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
