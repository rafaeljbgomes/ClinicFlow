package com.clinicflow.auth.application.results;

import com.clinicflow.auth.domain.Role;

import java.util.UUID;

public record UserResult(UUID id, String email, Role role, String fullName, boolean enabled) {
}
