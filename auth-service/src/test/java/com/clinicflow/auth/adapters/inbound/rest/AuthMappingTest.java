package com.clinicflow.auth.adapters.inbound.rest;

import com.clinicflow.auth.adapters.inbound.rest.dto.LoginRequest;
import com.clinicflow.auth.adapters.inbound.rest.dto.RegisterUserRequest;
import com.clinicflow.auth.adapters.inbound.rest.dto.UserResponse;
import com.clinicflow.auth.application.commands.LoginCommand;
import com.clinicflow.auth.application.commands.RegisterUserCommand;
import com.clinicflow.auth.application.mapping.UserApplicationMapper;
import com.clinicflow.auth.application.queries.GetUserQuery;
import com.clinicflow.auth.application.results.UserResult;
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
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private final AuthRestMapper restMapper = Mappers.getMapper(AuthRestMapper.class);
    private final UserApplicationMapper applicationMapper = Mappers.getMapper(UserApplicationMapper.class);

    @Test
    void registerRequestMapsToNormalizedCommand() {
        // Arrange
        RegisterUserRequest request = new RegisterUserRequest(
                " USER@Example.com ", "TestingPass123!", Role.PSYCHOLOGIST, "  User Name ");

        // Act
        RegisterUserCommand command = restMapper.toCommand(request);

        // Assert
        assertThat(command.email()).isEqualTo(new EmailAddress("user@example.com"));
        assertThat(command.fullName()).isEqualTo(new FullName("User Name"));
    }

    @Test
    void loginRequestMapsToNormalizedCommand() {
        // Arrange
        LoginRequest request = new LoginRequest(" USER@Example.com ", "TestingPass123!");

        // Act
        LoginCommand command = restMapper.toCommand(request);

        // Assert
        assertThat(command.email()).isEqualTo(new EmailAddress("user@example.com"));
    }

    @Test
    void userIdMapsToQuery() {
        // Act
        GetUserQuery query = restMapper.toQuery(USER_ID);

        // Assert
        assertThat(query.userId()).isEqualTo(new UserId(USER_ID));
    }

    @Test
    void userMapsToApplicationResult() {
        // Arrange
        User user = User.create(
                new UserId(USER_ID),
                new EmailAddress("user@example.com"),
                new PasswordHash("encoded-password"),
                Role.PSYCHOLOGIST,
                new FullName("User Name"),
                Instant.parse("2026-05-26T10:00:00Z")
        );

        // Act
        UserResult result = applicationMapper.toResult(user);

        // Assert
        assertThat(result).isEqualTo(new UserResult(USER_ID, "user@example.com", Role.PSYCHOLOGIST,
                "User Name", true));
    }

    @Test
    void applicationResultMapsToResponse() {
        // Arrange
        UserResult result = new UserResult(USER_ID, "user@example.com", Role.PSYCHOLOGIST, "User Name", true);

        // Act
        UserResponse response = restMapper.toResponse(result);

        // Assert
        assertThat(response.id()).isEqualTo(USER_ID);
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.fullName()).isEqualTo("User Name");
        assertThat(response.role()).isEqualTo(Role.PSYCHOLOGIST);
    }
}
