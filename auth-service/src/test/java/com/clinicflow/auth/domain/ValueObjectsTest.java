package com.clinicflow.auth.domain;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ValueObjectsTest {
    @Test
    void normalizesAndComparesEmailByValue() {
        // Act
        EmailAddress email = new EmailAddress(" USER@Example.COM ");

        // Assert
        assertThat(email).isEqualTo(new EmailAddress("user@example.com"));
    }

    @Test
    void userIdExposesTheWrappedUuid() {
        // Arrange
        UUID id = UUID.randomUUID();

        // Act
        UserId userId = new UserId(id);

        // Assert
        assertThat(userId.value()).isEqualTo(id);
    }

    @Test
    void fullNameTrimsSurroundingWhitespace() {
        // Act
        FullName fullName = new FullName("  Clinic User  ");

        // Assert
        assertThat(fullName.value()).isEqualTo("Clinic User");
    }

    @Test
    void passwordHashKeepsTheEncodedValue() {
        // Act
        PasswordHash hash = new PasswordHash("$2a$10$hash");

        // Assert
        assertThat(hash.value()).isEqualTo("$2a$10$hash");
    }

    static Stream<Arguments> invalidValues() {
        return Stream.of(
                Arguments.of("malformed email", (ThrowingCallable) () -> new EmailAddress("not-email")),
                Arguments.of("missing email", (ThrowingCallable) () -> new EmailAddress(null)),
                Arguments.of("missing user id", (ThrowingCallable) () -> new UserId(null)),
                Arguments.of("missing full name", (ThrowingCallable) () -> new FullName(null)),
                Arguments.of("blank full name", (ThrowingCallable) () -> new FullName(" ")),
                Arguments.of("missing password hash", (ThrowingCallable) () -> new PasswordHash(null)),
                Arguments.of("blank password hash", (ThrowingCallable) () -> new PasswordHash(" ")));
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidValues")
    void rejectsInvalidValue(String scenario, ThrowingCallable construct) {
        // Act
        Throwable thrown = catchThrowable(construct);

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }
}
