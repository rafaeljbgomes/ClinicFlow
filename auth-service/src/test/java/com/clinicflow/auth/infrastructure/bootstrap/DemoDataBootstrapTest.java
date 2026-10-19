package com.clinicflow.auth.infrastructure.bootstrap;

import com.clinicflow.auth.application.ports.PasswordHasher;
import com.clinicflow.auth.application.ports.UserRepository;
import com.clinicflow.auth.domain.EmailAddress;
import com.clinicflow.auth.domain.PasswordHash;
import com.clinicflow.auth.domain.Role;
import com.clinicflow.auth.domain.User;
import com.clinicflow.auth.domain.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DemoDataBootstrapTest {
    @Test
    void canBeConstructedBySpring() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            // Arrange
            context.getEnvironment().getPropertySources().addFirst(demoBootstrapProperty());
            context.registerBean(UserRepository.class, InMemoryUserRepository::new);
            context.registerBean(PasswordHasher.class, DemoPasswordHasher::new);
            context.registerBean(DemoDataBootstrap.class);

            // Act
            context.refresh();

            // Assert
            assertThat(context.getBean(DemoDataBootstrap.class)).isNotNull();
        }
    }

    @Test
    void seedsDemoUsersForEveryRole() throws Exception {
        // Arrange
        InMemoryUserRepository users = new InMemoryUserRepository();
        DemoDataBootstrap bootstrap = bootstrap(users);

        // Act
        bootstrap.run(null);

        // Assert
        assertThat(users.records().values()).extracting(User::role)
                .contains(Role.ADMIN, Role.PSYCHOLOGIST, Role.PATIENT);
    }

    @Test
    void seedsTheAdministratorDemoAccount() throws Exception {
        // Arrange
        InMemoryUserRepository users = new InMemoryUserRepository();
        DemoDataBootstrap bootstrap = bootstrap(users);

        // Act
        bootstrap.run(null);

        // Assert
        assertThat(users.findByEmail(new EmailAddress("admin@demo.clinicflow.local")))
                .hasValueSatisfying(user -> assertThat(user.role()).isEqualTo(Role.ADMIN));
    }

    @Test
    void repeatedRunDoesNotDuplicateDemoUsers() throws Exception {
        // Arrange
        InMemoryUserRepository users = new InMemoryUserRepository();
        DemoDataBootstrap bootstrap = bootstrap(users);
        bootstrap.run(null);

        // Act
        bootstrap.run(null);

        // Assert
        assertThat(users.records()).hasSize(5);
    }

    private static DemoDataBootstrap bootstrap(InMemoryUserRepository users) {
        return new DemoDataBootstrap(
                users,
                new DemoPasswordHasher(),
                Clock.fixed(Instant.parse("2026-07-05T10:00:00Z"), ZoneOffset.UTC)
        );
    }

    private static MapPropertySource demoBootstrapProperty() {
        return new MapPropertySource("test", Map.of("clinicflow.demo.bootstrap.enabled", "true"));
    }

    private static final class DemoPasswordHasher implements PasswordHasher {
        @Override
        public PasswordHash hash(String rawPassword) {
            return new PasswordHash("{demo}" + rawPassword);
        }

        @Override
        public boolean matches(String rawPassword, PasswordHash passwordHash) {
            return passwordHash.value().equals("{demo}" + rawPassword);
        }
    }

    private static final class InMemoryUserRepository implements UserRepository {
        private final Map<UserId, User> records = new LinkedHashMap<>();

        @Override
        public boolean existsByEmail(EmailAddress email) {
            return findByEmail(email).isPresent();
        }

        @Override
        public void save(User user) {
            records.put(user.id(), user);
        }

        @Override
        public Optional<User> findByEmail(EmailAddress email) {
            return records.values().stream().filter(user -> user.email().equals(email)).findFirst();
        }

        @Override
        public Optional<User> findById(UserId id) {
            return Optional.ofNullable(records.get(id));
        }

        Map<UserId, User> records() {
            return records;
        }
    }
}
