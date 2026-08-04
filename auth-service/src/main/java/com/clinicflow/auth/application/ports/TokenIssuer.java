package com.clinicflow.auth.application.ports;

import com.clinicflow.auth.domain.User;

public interface TokenIssuer {
    IssuedToken issue(User user);

    record IssuedToken(String value, long expiresInSeconds) {
    }
}
