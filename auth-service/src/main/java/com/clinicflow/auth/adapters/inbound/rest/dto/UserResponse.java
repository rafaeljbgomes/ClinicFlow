package com.clinicflow.auth.adapters.inbound.rest.dto;

import com.clinicflow.auth.domain.Role;

import java.util.UUID;

public record UserResponse(UUID id, String email, Role role, String fullName, boolean enabled) {
}
