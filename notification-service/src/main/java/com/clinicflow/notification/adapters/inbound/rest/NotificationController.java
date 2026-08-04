package com.clinicflow.notification.adapters.inbound.rest;

import com.clinicflow.notification.adapters.inbound.rest.dto.NotificationResponse;
import com.clinicflow.notification.application.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import com.clinicflow.notification.domain.PsychologistId;

import java.util.List;
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
    public List<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt) {
        var notifications = isAdmin(jwt)
                ? notificationService.list()
                : notificationService.listForPsychologist(psychologistId(jwt));
        return notifications.stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    public NotificationResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        var query = mapper.toQuery(id);
        return mapper.toResponse(isAdmin(jwt)
                ? notificationService.get(query)
                : notificationService.getForPsychologist(query, psychologistId(jwt)));
    }

    private boolean isAdmin(Jwt jwt) {
        return "ADMIN".equals(jwt.getClaimAsString("role"));
    }

    private PsychologistId psychologistId(Jwt jwt) {
        return new PsychologistId(UUID.fromString(jwt.getClaimAsString("userId")));
    }
}
