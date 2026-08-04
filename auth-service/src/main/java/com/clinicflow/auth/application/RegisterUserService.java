package com.clinicflow.auth.application;

import com.clinicflow.auth.application.commands.RegisterUserCommand;
import com.clinicflow.auth.application.exceptions.DuplicateEmailException;
import com.clinicflow.auth.application.mapping.UserApplicationMapper;
import com.clinicflow.auth.application.ports.PasswordHasher;
import com.clinicflow.auth.application.ports.UserRepository;
import com.clinicflow.auth.application.results.UserResult;
import com.clinicflow.auth.domain.Role;
import com.clinicflow.auth.domain.User;
import com.clinicflow.auth.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RegisterUserService {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final UserApplicationMapper mapper;

    public RegisterUserService(UserRepository userRepository, PasswordHasher passwordHasher,
                               UserApplicationMapper mapper) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.mapper = mapper;
    }

    @Transactional
    public UserResult register(RegisterUserCommand command) {
        Role role = command.role() == Role.ADMIN ? Role.PSYCHOLOGIST : command.role();
        if (userRepository.existsByEmail(command.email())) {
            throw new DuplicateEmailException();
        }
        User user = User.create(
                new UserId(UUID.randomUUID()),
                command.email(),
                passwordHasher.hash(command.password()),
                role,
                command.fullName(),
                Instant.now()
        );
        userRepository.save(user);
        return mapper.toResult(user);
    }
}
