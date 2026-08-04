package com.clinicflow.auth.application.commands;

import com.clinicflow.auth.domain.EmailAddress;

public record LoginCommand(EmailAddress email, String password) {
    public LoginCommand {
        if (email == null || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Login fields are missing");
        }
    }
}
