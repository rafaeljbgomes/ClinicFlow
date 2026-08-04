package com.clinicflow.auth.application;

import com.clinicflow.auth.application.commands.RegisterUserCommand;
import com.clinicflow.auth.application.exceptions.DuplicateEmailException;
import com.clinicflow.auth.application.mapping.UserApplicationMapper;
import com.clinicflow.auth.application.ports.PasswordHasher;
import com.clinicflow.auth.application.ports.UserRepository;
import com.clinicflow.auth.domain.EmailAddress;
import com.clinicflow.auth.domain.FullName;
import com.clinicflow.auth.domain.PasswordHash;
import com.clinicflow.auth.domain.Role;
import com.clinicflow.auth.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterUserServiceTest {
    @Mock
    private UserRepository repository;
    @Mock
    private PasswordHasher passwordHasher;

    private RegisterUserService service;

    @BeforeEach
    void setUp() {
        UserApplicationMapper mapper = Mappers.getMapper(UserApplicationMapper.class);
        service = new RegisterUserService(repository, passwordHasher, mapper);
    }

    @Test
    void registersUserWithHashedPassword() {
        RegisterUserCommand command = command(Role.PSYCHOLOGIST);
        when(passwordHasher.hash(command.password())).thenReturn(new PasswordHash("encoded"));

        var result = service.register(command);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.passwordHash()).isEqualTo(new PasswordHash("encoded"));
        assertThat(saved.role()).isEqualTo(Role.PSYCHOLOGIST);
        assertThat(saved.enabled()).isTrue();
        assertThat(result.id()).isEqualTo(saved.id().value());
        assertThat(result.email()).isEqualTo("user@example.com");
    }

    @Test
    void preventsPublicAdminRegistration() {
        RegisterUserCommand command = command(Role.ADMIN);
        when(passwordHasher.hash(command.password())).thenReturn(new PasswordHash("encoded"));

        service.register(command);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().role()).isEqualTo(Role.PSYCHOLOGIST);
    }

    @Test
    void rejectsDuplicateEmailBeforeHashingOrSaving() {
        RegisterUserCommand command = command(Role.PSYCHOLOGIST);
        when(repository.existsByEmail(command.email())).thenReturn(true);

        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(DuplicateEmailException.class);

        verify(passwordHasher, never()).hash(command.password());
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private RegisterUserCommand command(Role role) {
        return new RegisterUserCommand(
                new EmailAddress("User@Example.com"),
                "TestingPass123!",
                role,
                new FullName("Clinic User")
        );
    }
}
