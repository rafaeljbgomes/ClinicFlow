package com.clinicflow.auth.adapters.inbound.rest;

import com.clinicflow.auth.adapters.inbound.rest.dto.LoginRequest;
import com.clinicflow.auth.adapters.inbound.rest.dto.LoginResponse;
import com.clinicflow.auth.adapters.inbound.rest.dto.RegisterUserRequest;
import com.clinicflow.auth.adapters.inbound.rest.dto.UserResponse;
import com.clinicflow.auth.application.LoginResult;
import com.clinicflow.auth.application.commands.LoginCommand;
import com.clinicflow.auth.application.commands.RegisterUserCommand;
import com.clinicflow.auth.application.mapping.MapperConfiguration;
import com.clinicflow.auth.application.queries.GetUserQuery;
import com.clinicflow.auth.application.results.UserResult;
import com.clinicflow.auth.domain.EmailAddress;
import com.clinicflow.auth.domain.FullName;
import com.clinicflow.auth.domain.UserId;
import org.mapstruct.Mapper;

import java.util.UUID;

@Mapper(config = MapperConfiguration.class)
public abstract class AuthRestMapper {
    public RegisterUserCommand toCommand(RegisterUserRequest request) {
        return new RegisterUserCommand(
                new EmailAddress(request.email()),
                request.password(),
                request.role(),
                new FullName(request.fullName())
        );
    }

    public LoginCommand toCommand(LoginRequest request) {
        return new LoginCommand(new EmailAddress(request.email()), request.password());
    }

    public GetUserQuery toQuery(UUID userId) {
        return new GetUserQuery(new UserId(userId));
    }

    public abstract UserResponse toResponse(UserResult result);
    public abstract LoginResponse toResponse(LoginResult result);
}
