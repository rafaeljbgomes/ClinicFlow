package com.clinicflow.auth.adapters.inbound.rest.dto;

import com.clinicflow.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotNull Role role,
        @NotBlank @Size(max = 160) String fullName
) {
}
