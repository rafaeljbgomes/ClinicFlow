package com.clinicflow.auth.adapters.inbound.rest;

import com.clinicflow.auth.adapters.inbound.rest.dto.LoginRequest;
import com.clinicflow.auth.adapters.inbound.rest.dto.RegisterUserRequest;
import com.clinicflow.auth.application.mapping.UserApplicationMapper;
import com.clinicflow.auth.domain.EmailAddress;
import com.clinicflow.auth.domain.FullName;
import com.clinicflow.auth.domain.PasswordHash;
import com.clinicflow.auth.domain.Role;
import com.clinicflow.auth.domain.User;
import com.clinicflow.auth.domain.UserId;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthMappingTest {
    private final AuthRestMapper restMapper = Mappers.getMapper(AuthRestMapper.class);
    private final UserApplicationMapper applicationMapper = Mappers.getMapper(UserApplicationMapper.class);

    @Test
    void mapsRestInputsAndApplicationOutputs() {
        UUID userId = UUID.randomUUID();
        RegisterUserRequest register = new RegisterUserRequest(
                " USER@Example.com ", "TestingPass123!", Role.PSYCHOLOGIST, "  User Name ");

        var registerCommand = restMapper.toCommand(register);
        var loginCommand = restMapper.toCommand(new LoginRequest(" USER@Example.com ", "TestingPass123!"));
        var query = restMapper.toQuery(userId);

        assertThat(registerCommand.email()).isEqualTo(new EmailAddress("user@example.com"));
        assertThat(registerCommand.fullName()).isEqualTo(new FullName("User Name"));
        assertThat(loginCommand.email()).isEqualTo(new EmailAddress("user@example.com"));
        assertThat(query.userId()).isEqualTo(new UserId(userId));

        User user = User.create(
                new UserId(userId),
                registerCommand.email(),
                new PasswordHash("encoded-password"),
                registerCommand.role(),
                registerCommand.fullName(),
                Instant.parse("2026-05-26T10:00:00Z")
        );

        var result = applicationMapper.toResult(user);
        var response = restMapper.toResponse(result);

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.fullName()).isEqualTo("User Name");
        assertThat(response.role()).isEqualTo(Role.PSYCHOLOGIST);
    }
}
