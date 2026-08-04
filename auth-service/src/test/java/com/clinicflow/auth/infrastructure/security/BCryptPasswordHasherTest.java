package com.clinicflow.auth.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class BCryptPasswordHasherTest {
    private final BCryptPasswordHasher hasher =
            new BCryptPasswordHasher(new BCryptPasswordEncoder(4));

    @Test
    void hashesAndVerifiesPasswordWithoutStoringPlainText() {
        var hash = hasher.hash("TestingPass123!");

        assertThat(hash.value()).doesNotContain("TestingPass123!");
        assertThat(hasher.matches("TestingPass123!", hash)).isTrue();
        assertThat(hasher.matches("wrong-password", hash)).isFalse();
    }
}
