package com.clinicflow.auth.application.commands;

import com.clinicflow.auth.domain.EmailAddress;
import com.clinicflow.auth.domain.FullName;
import com.clinicflow.auth.domain.Role;

public record RegisterUserCommand(EmailAddress email, String password, Role role, FullName fullName) {
    public RegisterUserCommand {
        if (email == null || password == null || password.isBlank() || role == null || fullName == null) {
            throw new IllegalArgumentException("Registration fields are missing");
        }
    }
}
