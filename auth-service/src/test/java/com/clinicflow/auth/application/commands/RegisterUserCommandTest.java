package com.clinicflow.auth.application.commands;

import com.clinicflow.auth.domain.EmailAddress;
import com.clinicflow.auth.domain.FullName;
import com.clinicflow.auth.domain.Role;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class RegisterUserCommandTest {
    static Stream<Arguments> missingFields() {
        EmailAddress email = new EmailAddress("user@example.com");
        FullName fullName = new FullName("Clinic User");
        return Stream.of(
                Arguments.of("missing email", null, "password", Role.PSYCHOLOGIST, fullName),
                Arguments.of("missing password", email, null, Role.PSYCHOLOGIST, fullName),
                Arguments.of("blank password", email, " ", Role.PSYCHOLOGIST, fullName),
                Arguments.of("missing role", email, "password", null, fullName),
                Arguments.of("missing full name", email, "password", Role.PSYCHOLOGIST, null));
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("missingFields")
    void rejectsMissingOrBlankField(String scenario, EmailAddress email, String password, Role role,
                                    FullName fullName) {
        // Act
        Throwable thrown = catchThrowable(() -> new RegisterUserCommand(email, password, role, fullName));

        // Assert
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Registration fields are missing");
    }
}
