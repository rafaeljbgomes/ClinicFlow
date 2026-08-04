package com.clinicflow.auth.application.commands;

import com.clinicflow.auth.domain.EmailAddress;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginCommandTest {
    @Test
    void rejectsMissingOrBlankCredentials() {
        EmailAddress email = new EmailAddress("user@example.com");

        assertInvalid(null, "password");
        assertInvalid(email, null);
        assertInvalid(email, " ");
    }

    private void assertInvalid(EmailAddress email, String password) {
        assertThatThrownBy(() -> new LoginCommand(email, password))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Login fields are missing");
    }
}
