package com.clinicflow.auth.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueObjectsTest {
    @Test
    void normalizesAndComparesEmailByValue() {
        assertThat(new EmailAddress(" USER@Example.COM "))
                .isEqualTo(new EmailAddress("user@example.com"));
    }

    @Test
    void validatesAuthValueObjects() {
        UUID id = UUID.randomUUID();
        assertThat(new UserId(id).value()).isEqualTo(id);
        assertThat(new FullName("  Clinic User  ").value()).isEqualTo("Clinic User");
        assertThat(new PasswordHash("$2a$10$hash").value()).isEqualTo("$2a$10$hash");
        assertThatThrownBy(() -> new EmailAddress("not-email")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailAddress(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FullName(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FullName(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PasswordHash(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PasswordHash(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
