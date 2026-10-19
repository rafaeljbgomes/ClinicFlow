package com.clinicflow.auth.application;

import com.clinicflow.auth.application.commands.LoginCommand;
import com.clinicflow.auth.application.exceptions.InvalidCredentialsException;
import com.clinicflow.auth.application.mapping.UserApplicationMapper;
import com.clinicflow.auth.application.ports.PasswordHasher;
import com.clinicflow.auth.application.ports.TokenIssuer;
import com.clinicflow.auth.application.ports.UserRepository;
import com.clinicflow.auth.domain.EmailAddress;
import com.clinicflow.auth.domain.FullName;
import com.clinicflow.auth.domain.PasswordHash;
import com.clinicflow.auth.domain.Role;
import com.clinicflow.auth.domain.User;
import com.clinicflow.auth.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {
    @Mock
    private UserRepository repository;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private TokenIssuer tokenIssuer;

    private LoginService service;
    private LoginCommand command;

    @BeforeEach
    void setUp() {
        service = new LoginService(repository, passwordHasher, tokenIssuer,
                Mappers.getMapper(UserApplicationMapper.class));
        command = new LoginCommand(new EmailAddress("user@example.com"), "TestingPass123!");
    }

    @Test
    void authenticatesEnabledUserAndReturnsIssuedToken() {
        // Arrange
        User user = user(true);
        when(repository.findByEmail(command.email())).thenReturn(Optional.of(user));
        when(passwordHasher.matches(command.password(), user.passwordHash())).thenReturn(true);
        when(tokenIssuer.issue(user)).thenReturn(new TokenIssuer.IssuedToken("jwt", 1800));

        // Act
        LoginResult result = service.login(command);

        // Assert
        assertThat(result.accessToken()).isEqualTo("jwt");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresInSeconds()).isEqualTo(1800);
        assertThat(result.user().id()).isEqualTo(user.id().value());
    }

    @Test
    void rejectsUnknownEmail() {
        // Arrange
        when(repository.findByEmail(command.email())).thenReturn(Optional.empty());

        // Act
        Throwable thrown = catchThrowable(() -> service.login(command));

        // Assert
        assertThat(thrown).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void unknownEmailStopsBeforeCredentialVerification() {
        // Arrange
        when(repository.findByEmail(command.email())).thenReturn(Optional.empty());

        // Act
        catchThrowable(() -> service.login(command));

        // Assert
        verifyNoInteractions(passwordHasher, tokenIssuer);
    }

    @Test
    void rejectsDisabledUser() {
        // Arrange
        when(repository.findByEmail(command.email())).thenReturn(Optional.of(user(false)));

        // Act
        Throwable thrown = catchThrowable(() -> service.login(command));

        // Assert
        assertThat(thrown).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void disabledUserStopsBeforeCredentialVerification() {
        // Arrange
        when(repository.findByEmail(command.email())).thenReturn(Optional.of(user(false)));

        // Act
        catchThrowable(() -> service.login(command));

        // Assert
        verifyNoInteractions(passwordHasher, tokenIssuer);
    }

    @Test
    void rejectsWrongPassword() {
        // Arrange
        User user = user(true);
        when(repository.findByEmail(command.email())).thenReturn(Optional.of(user));
        when(passwordHasher.matches(command.password(), user.passwordHash())).thenReturn(false);

        // Act
        Throwable thrown = catchThrowable(() -> service.login(command));

        // Assert
        assertThat(thrown).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void wrongPasswordDoesNotIssueToken() {
        // Arrange
        User user = user(true);
        when(repository.findByEmail(command.email())).thenReturn(Optional.of(user));
        when(passwordHasher.matches(command.password(), user.passwordHash())).thenReturn(false);

        // Act
        catchThrowable(() -> service.login(command));

        // Assert
        verifyNoInteractions(tokenIssuer);
    }

    private User user(boolean enabled) {
        return User.rehydrate(
                new UserId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                command.email(),
                new PasswordHash("encoded"),
                Role.PSYCHOLOGIST,
                new FullName("Clinic User"),
                enabled,
                Instant.parse("2026-06-01T10:00:00Z")
        );
    }
}
