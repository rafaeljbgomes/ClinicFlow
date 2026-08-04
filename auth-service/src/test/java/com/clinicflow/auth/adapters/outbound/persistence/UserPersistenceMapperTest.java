package com.clinicflow.auth.adapters.outbound.persistence;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserPersistenceMapperTest {
    private final UserPersistenceMapper mapper = Mappers.getMapper(UserPersistenceMapper.class);

    @Test
    void roundTripsDomainAndPersistenceModels() {
        User user = user();

        User restored = mapper.toDomain(mapper.toJpa(user));

        assertThat(restored).isEqualTo(user);
        assertThat(restored.email()).isEqualTo(user.email());
        assertThat(restored.fullName()).isEqualTo(user.fullName());
    }

    @Test
    void repositorySavePersistsWithoutReturningAReplacementEntity() {
        SpringDataUserJpaRepository delegate = mock(SpringDataUserJpaRepository.class);
        UserPersistenceMapper mockedMapper = mock(UserPersistenceMapper.class);
        User user = user();
        JpaUserEntity jpa = mapper.toJpa(user);
        when(mockedMapper.toJpa(user)).thenReturn(jpa);

        new JpaUserRepository(delegate, mockedMapper).save(user);

        verify(delegate).save(jpa);
        assertThat(user.email()).isEqualTo(new EmailAddress("user@example.com"));
    }

    private User user() {
        return User.create(new UserId(UUID.randomUUID()), new EmailAddress("user@example.com"),
                new PasswordHash("hash"), Role.PSYCHOLOGIST, new FullName("User Name"),
                Instant.parse("2026-05-26T10:00:00Z"));
    }
}
