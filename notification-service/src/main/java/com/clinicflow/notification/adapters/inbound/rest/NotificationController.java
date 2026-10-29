package com.clinicflow.notification.adapters.inbound.rest;

import com.clinicflow.notification.adapters.inbound.rest.dto.NotificationResponse;
import com.clinicflow.notification.adapters.inbound.rest.dto.PageResponse;
import com.clinicflow.notification.application.NotificationService;
import com.clinicflow.notification.application.queries.PageQuery;
import com.clinicflow.notification.domain.NotificationStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.clinicflow.notification.domain.PsychologistId;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@PreAuthorize("hasAnyRole('ADMIN','PSYCHOLOGIST')")
public class NotificationController {
    private final NotificationService notificationService;
    private final NotificationRestMapper mapper;

    public NotificationController(NotificationService notificationService, NotificationRestMapper mapper) {
        this.notificationService = notificationService;
        this.mapper = mapper;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(JwtAuthenticationToken authentication,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE) int size,
                                                   @RequestParam(required = false) Set<NotificationStatus> status) {
        var query = mapper.toListQuery(page, size, status);
        var notifications = isAdmin(authentication)
                ? notificationService.list(query)
                : notificationService.listForPsychologist(query, psychologistId(authentication.getToken()));
        return PageResponse.of(notifications, mapper::toResponse);
    }

    @GetMapping("/{id}")
    public NotificationResponse get(JwtAuthenticationToken authentication, @PathVariable UUID id) {
        var query = mapper.toQuery(id);
        return mapper.toResponse(isAdmin(authentication)
                ? notificationService.get(query)
                : notificationService.getForPsychologist(query, psychologistId(authentication.getToken())));
    }

    // Authorities are mapped from the token's roles claim, the same source as @PreAuthorize above.
    private boolean isAdmin(JwtAuthenticationToken authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private PsychologistId psychologistId(Jwt jwt) {
        return new PsychologistId(UUID.fromString(jwt.getSubject()));
    }
}
