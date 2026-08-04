package com.clinicflow.auth.infrastructure.security;

import com.clinicflow.auth.application.ports.PasswordHasher;
import com.clinicflow.auth.domain.PasswordHash;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordHasher implements PasswordHasher {
    private final PasswordEncoder passwordEncoder;

    public BCryptPasswordHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public PasswordHash hash(String rawPassword) {
        return new PasswordHash(passwordEncoder.encode(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash.value());
    }
}
