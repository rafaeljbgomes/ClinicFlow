package com.clinicflow.auth.adapters.outbound.persistence;

import com.clinicflow.auth.domain.EmailAddress;
import com.clinicflow.auth.domain.FullName;
import com.clinicflow.auth.domain.PasswordHash;
import com.clinicflow.auth.domain.Role;
import com.clinicflow.auth.domain.User;
import com.clinicflow.auth.domain.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaUserRepository.class, UserPersistenceMapperImpl.class})
@Testcontainers
class JpaUserRepositoryIT {
    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(org.testcontainers.utility.DockerImageName
                    .parse("postgres:16.14-alpine@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired
    private JpaUserRepository repository;
    @Autowired
    private SpringDataUserJpaRepository delegate;

    @Test
    void persistsAndFindsUserThroughEveryRepositoryOperation() {
        User user = user(UUID.randomUUID(), "user@example.com");

        repository.save(user);
        delegate.flush();

        assertThat(repository.existsByEmail(user.email())).isTrue();
        assertThat(repository.findByEmail(user.email())).contains(user);
        assertThat(repository.findById(user.id())).contains(user);
    }

    @Test
    void enforcesUniqueEmailConstraint() {
        repository.save(user(UUID.randomUUID(), "duplicate@example.com"));
        delegate.flush();

        repository.save(user(UUID.randomUUID(), "duplicate@example.com"));

        assertThatThrownBy(delegate::flush)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User user(UUID id, String email) {
        return User.create(new UserId(id), new EmailAddress(email), new PasswordHash("encoded"),
                Role.PSYCHOLOGIST, new FullName("Clinic User"),
                Instant.parse("2026-06-01T10:00:00Z"));
    }
}
