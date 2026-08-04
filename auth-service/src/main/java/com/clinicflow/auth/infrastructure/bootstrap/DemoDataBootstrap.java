package com.clinicflow.auth.infrastructure.bootstrap;

import com.clinicflow.auth.application.ports.PasswordHasher;
import com.clinicflow.auth.application.ports.UserRepository;
import com.clinicflow.auth.domain.EmailAddress;
import com.clinicflow.auth.domain.FullName;
import com.clinicflow.auth.domain.Role;
import com.clinicflow.auth.domain.User;
import com.clinicflow.auth.domain.UserId;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "clinicflow.demo.bootstrap", name = "enabled", havingValue = "true")
public class DemoDataBootstrap implements ApplicationRunner {
    public static final String DEMO_PASSWORD = "ClinicFlowDemo!2026";

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    @Autowired
    public DemoDataBootstrap(UserRepository users, PasswordHasher passwordHasher) {
        this(users, passwordHasher, Clock.systemUTC());
    }

    DemoDataBootstrap(UserRepository users, PasswordHasher passwordHasher, Clock clock) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        for (DemoUser user : demoUsers()) {
            users.save(User.create(
                    new UserId(user.id()),
                    new EmailAddress(user.email()),
                    passwordHasher.hash(DEMO_PASSWORD),
                    user.role(),
                    new FullName(user.fullName()),
                    now
            ));
        }
    }

    private static List<DemoUser> demoUsers() {
        return List.of(
                new DemoUser(uuid("00000000-0000-4000-8000-000000000001"),
                        "admin@demo.clinicflow.local", Role.ADMIN, "ClinicFlow Admin"),
                new DemoUser(uuid("00000000-0000-4000-8000-000000000011"),
                        "sofia.almeida@demo.clinicflow.local", Role.PSYCHOLOGIST, "Dra. Sofia Almeida"),
                new DemoUser(uuid("00000000-0000-4000-8000-000000000012"),
                        "miguel.santos@demo.clinicflow.local", Role.PSYCHOLOGIST, "Dr. Miguel Santos"),
                new DemoUser(uuid("00000000-0000-4000-8000-000000000021"),
                        "ana.martins@demo.clinicflow.local", Role.PATIENT, "Ana Martins"),
                new DemoUser(uuid("00000000-0000-4000-8000-000000000022"),
                        "joao.ferreira@demo.clinicflow.local", Role.PATIENT, "Joao Ferreira")
        );
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

    private record DemoUser(UUID id, String email, Role role, String fullName) {
    }
}
