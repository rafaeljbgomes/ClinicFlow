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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        User user = user(true);
        when(repository.findByEmail(command.email())).thenReturn(Optional.of(user));
        when(passwordHasher.matches(command.password(), user.passwordHash())).thenReturn(true);
        when(tokenIssuer.issue(user)).thenReturn(new TokenIssuer.IssuedToken("jwt", 1800));

        LoginResult result = service.login(command);

        assertThat(result.accessToken()).isEqualTo("jwt");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresInSeconds()).isEqualTo(1800);
        assertThat(result.user().id()).isEqualTo(user.id().value());
    }

    @Test
    void rejectsMissingUserWithoutCheckingPassword() {
        when(repository.findByEmail(command.email())).thenReturn(Optional.empty());

        assertInvalidCredentials();

        verify(passwordHasher, never()).matches(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void rejectsDisabledUserWithoutCheckingPassword() {
        when(repository.findByEmail(command.email())).thenReturn(Optional.of(user(false)));

        assertInvalidCredentials();

        verify(passwordHasher, never()).matches(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void rejectsWrongPasswordWithoutIssuingToken() {
        User user = user(true);
        when(repository.findByEmail(command.email())).thenReturn(Optional.of(user));
        when(passwordHasher.matches(command.password(), user.passwordHash())).thenReturn(false);

        assertInvalidCredentials();

        verify(tokenIssuer, never()).issue(org.mockito.ArgumentMatchers.any());
    }

    private void assertInvalidCredentials() {
        assertThatThrownBy(() -> service.login(command))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(tokenIssuer, never()).issue(org.mockito.ArgumentMatchers.any());
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
