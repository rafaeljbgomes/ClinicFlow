package com.clinicflow.auth.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class UserTest {
    private static final UserId ID = new UserId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private static final Instant CREATED_AT = Instant.parse("2026-05-26T10:00:00Z");

    @Test
    void usersWithTheSameIdAreEqual() {
        // Arrange
        User first = user();
        User rehydrated = User.rehydrate(ID, new EmailAddress("other@example.com"), new PasswordHash("other"),
                Role.ADMIN, new FullName("Other User"), false, CREATED_AT);

        // Act
        boolean equal = first.equals(rehydrated);

        // Assert
        assertThat(equal).isTrue();
    }

    @Test
    void userIsNotEqualToAnotherType() {
        // Arrange
        User user = user();

        // Act
        boolean equal = user.equals(new Object());

        // Assert
        assertThat(equal).isFalse();
    }

    static Stream<Arguments> valueObjectAccessors() {
        return Stream.of(
                Arguments.of("id", (Function<User, Object>) User::id),
                Arguments.of("email", (Function<User, Object>) User::email),
                Arguments.of("full name", (Function<User, Object>) User::fullName),
                Arguments.of("password hash", (Function<User, Object>) User::passwordHash));
    }

    @ParameterizedTest(name = "{0} returns a defensive copy")
    @MethodSource("valueObjectAccessors")
    void accessorReturnsDefensiveValueObjectCopy(String accessor, Function<User, Object> read) {
        // Arrange
        User user = user();

        // Act
        Object firstRead = read.apply(user);
        Object secondRead = read.apply(user);

        // Assert
        assertThat(firstRead).isNotSameAs(secondRead);
    }

    static Stream<Arguments> requiredFields() {
        return Stream.of(
                Arguments.of("id", 0),
                Arguments.of("email", 1),
                Arguments.of("password hash", 2),
                Arguments.of("role", 3),
                Arguments.of("full name", 4),
                Arguments.of("created at", 5));
    }

    @ParameterizedTest(name = "rejects missing {0}")
    @MethodSource("requiredFields")
    void rejectsMissingRequiredField(String field, int index) {
        // Arrange
        Object[] fields = {
                new UserId(UUID.randomUUID()),
                new EmailAddress("user@example.com"),
                new PasswordHash("hash"),
                Role.PSYCHOLOGIST,
                new FullName("Clinic User"),
                CREATED_AT
        };
        fields[index] = null;

        // Act
        Throwable thrown = catchThrowable(() -> User.rehydrate(
                (UserId) fields[0],
                (EmailAddress) fields[1],
                (PasswordHash) fields[2],
                (Role) fields[3],
                (FullName) fields[4],
                true,
                (Instant) fields[5]));

        // Assert
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User required fields are missing");
    }

    private User user() {
        return User.create(ID, new EmailAddress("user@example.com"), new PasswordHash("hash"),
                Role.PSYCHOLOGIST, new FullName("User One"), CREATED_AT);
    }
}
