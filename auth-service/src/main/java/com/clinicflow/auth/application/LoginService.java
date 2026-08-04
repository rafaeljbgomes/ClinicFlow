package com.clinicflow.auth.application;

import com.clinicflow.auth.application.commands.LoginCommand;
import com.clinicflow.auth.application.exceptions.InvalidCredentialsException;
import com.clinicflow.auth.application.mapping.UserApplicationMapper;
import com.clinicflow.auth.application.ports.PasswordHasher;
import com.clinicflow.auth.application.ports.TokenIssuer;
import com.clinicflow.auth.application.ports.UserRepository;
import com.clinicflow.auth.domain.User;
import org.springframework.stereotype.Service;

@Service
public class LoginService {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final UserApplicationMapper mapper;

    public LoginService(UserRepository userRepository, PasswordHasher passwordHasher,
                        TokenIssuer tokenIssuer, UserApplicationMapper mapper) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.mapper = mapper;
    }

    public LoginResult login(LoginCommand command) {
        User user = userRepository.findByEmail(command.email())
                .filter(found -> found.enabled() && passwordHasher.matches(command.password(), found.passwordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        TokenIssuer.IssuedToken token = tokenIssuer.issue(user);
        return new LoginResult(token.value(), "Bearer", token.expiresInSeconds(), mapper.toResult(user));
    }
}
