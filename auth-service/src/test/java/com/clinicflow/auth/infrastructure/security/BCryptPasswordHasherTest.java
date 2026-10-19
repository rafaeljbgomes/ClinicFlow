package com.clinicflow.auth.infrastructure.security;

import com.clinicflow.auth.domain.PasswordHash;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class BCryptPasswordHasherTest {
    private final BCryptPasswordHasher hasher =
            new BCryptPasswordHasher(new BCryptPasswordEncoder(4));

    @Test
    void hashDoesNotStorePlainTextPassword() {
        // Act
        PasswordHash hash = hasher.hash("TestingPass123!");

        // Assert
        assertThat(hash.value()).doesNotContain("TestingPass123!");
    }

    @Test
    void matchesAcceptsTheOriginalPassword() {
        // Arrange
        PasswordHash hash = hasher.hash("TestingPass123!");

        // Act
        boolean matches = hasher.matches("TestingPass123!", hash);

        // Assert
        assertThat(matches).isTrue();
    }

    @Test
    void matchesRejectsADifferentPassword() {
        // Arrange
        PasswordHash hash = hasher.hash("TestingPass123!");

        // Act
        boolean matches = hasher.matches("wrong-password", hash);

        // Assert
        assertThat(matches).isFalse();
    }
}
