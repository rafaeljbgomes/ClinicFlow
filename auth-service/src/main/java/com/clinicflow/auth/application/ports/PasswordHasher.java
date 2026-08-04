package com.clinicflow.auth.application.ports;

import com.clinicflow.auth.domain.PasswordHash;

public interface PasswordHasher {
    PasswordHash hash(String rawPassword);
    boolean matches(String rawPassword, PasswordHash passwordHash);
}
