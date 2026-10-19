package com.clinicflow.auth.application.commands;

import com.clinicflow.auth.domain.EmailAddress;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class LoginCommandTest {
    static Stream<Arguments> missingCredentials() {
        EmailAddress email = new EmailAddress("user@example.com");
        return Stream.of(
                Arguments.of("missing email", null, "password"),
                Arguments.of("missing password", email, null),
                Arguments.of("blank password", email, " "));
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("missingCredentials")
    void rejectsMissingOrBlankCredential(String scenario, EmailAddress email, String password) {
        // Act
        Throwable thrown = catchThrowable(() -> new LoginCommand(email, password));

        // Assert
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Login fields are missing");
    }
}
