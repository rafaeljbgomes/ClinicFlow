package com.clinicflow.auth.application;

import com.clinicflow.auth.application.exceptions.UserNotFoundException;
import com.clinicflow.auth.application.mapping.UserApplicationMapper;
import com.clinicflow.auth.application.ports.UserRepository;
import com.clinicflow.auth.application.queries.GetUserQuery;
import com.clinicflow.auth.domain.EmailAddress;
import com.clinicflow.auth.domain.FullName;
import com.clinicflow.auth.domain.PasswordHash;
import com.clinicflow.auth.domain.Role;
import com.clinicflow.auth.domain.User;
import com.clinicflow.auth.domain.UserId;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserServiceTest {
    @Mock
    private UserRepository repository;

    @Test
    void returnsMappedUser() {
        UserId id = new UserId(UUID.randomUUID());
        User user = User.create(id, new EmailAddress("user@example.com"), new PasswordHash("hash"),
                Role.PSYCHOLOGIST, new FullName("Clinic User"), Instant.parse("2026-06-01T10:00:00Z"));
        when(repository.findById(id)).thenReturn(Optional.of(user));
        GetUserService service = new GetUserService(repository, Mappers.getMapper(UserApplicationMapper.class));

        var result = service.get(new GetUserQuery(id));

        assertThat(result.id()).isEqualTo(id.value());
        assertThat(result.email()).isEqualTo("user@example.com");
    }

    @Test
    void throwsWhenUserDoesNotExist() {
        UserId id = new UserId(UUID.randomUUID());
        when(repository.findById(id)).thenReturn(Optional.empty());
        GetUserService service = new GetUserService(repository, Mappers.getMapper(UserApplicationMapper.class));

        assertThatThrownBy(() -> service.get(new GetUserQuery(id)))
                .isInstanceOf(UserNotFoundException.class);
    }
}
