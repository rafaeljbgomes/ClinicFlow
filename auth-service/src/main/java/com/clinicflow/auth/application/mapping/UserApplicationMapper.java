package com.clinicflow.auth.application.mapping;

import com.clinicflow.auth.application.results.UserResult;
import com.clinicflow.auth.domain.User;
import org.mapstruct.Mapper;

@Mapper(config = MapperConfiguration.class)
public abstract class UserApplicationMapper {
    public abstract String copy(String value);

    public UserResult toResult(User user) {
        return new UserResult(
                user.id().value(),
                user.email().value(),
                user.role(),
                user.fullName().value(),
                user.enabled()
        );
    }
}
