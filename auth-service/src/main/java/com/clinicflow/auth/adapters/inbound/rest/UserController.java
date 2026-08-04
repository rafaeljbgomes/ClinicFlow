package com.clinicflow.auth.adapters.inbound.rest;

import com.clinicflow.auth.adapters.inbound.rest.dto.UserResponse;
import com.clinicflow.auth.application.GetUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {
    private final GetUserService getUserService;
    private final AuthRestMapper mapper;

    public UserController(GetUserService getUserService, AuthRestMapper mapper) {
        this.getUserService = getUserService;
        this.mapper = mapper;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(getUserService.get(
                mapper.toQuery(UUID.fromString(jwt.getClaimAsString("userId")))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse get(@PathVariable UUID id) {
        return mapper.toResponse(getUserService.get(mapper.toQuery(id)));
    }
}
