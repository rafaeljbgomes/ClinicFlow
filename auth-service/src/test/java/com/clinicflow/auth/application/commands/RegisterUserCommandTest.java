package com.clinicflow.auth.application.commands;

import com.clinicflow.auth.domain.EmailAddress;
import com.clinicflow.auth.domain.FullName;
import com.clinicflow.auth.domain.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegisterUserCommandTest {
    @Test
    void rejectsEveryMissingOrBlankField() {
        EmailAddress email = new EmailAddress("user@example.com");
        FullName fullName = new FullName("Clinic User");

        assertInvalid(null, "password", Role.PSYCHOLOGIST, fullName);
        assertInvalid(email, null, Role.PSYCHOLOGIST, fullName);
        assertInvalid(email, " ", Role.PSYCHOLOGIST, fullName);
        assertInvalid(email, "password", null, fullName);
        assertInvalid(email, "password", Role.PSYCHOLOGIST, null);
    }

    private void assertInvalid(EmailAddress email, String password, Role role, FullName fullName) {
        assertThatThrownBy(() -> new RegisterUserCommand(email, password, role, fullName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Registration fields are missing");
    }
}
