package com.clinicflow.auth.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {
    @Test
    void usesIdentityEqualityAndReturnsDefensiveValueObjectCopies() {
        UserId id = new UserId(UUID.randomUUID());
        User first = User.create(id, new EmailAddress("user@example.com"), new PasswordHash("hash"),
                Role.PSYCHOLOGIST, new FullName("User One"), Instant.parse("2026-05-26T10:00:00Z"));
        User rehydrated = User.rehydrate(id, new EmailAddress("other@example.com"), new PasswordHash("other"),
                Role.ADMIN, new FullName("Other User"), false, Instant.parse("2026-05-26T10:00:00Z"));

        assertThat(first).isEqualTo(rehydrated);
        assertThat(first.id()).isNotSameAs(first.id());
        assertThat(first.email()).isNotSameAs(first.email());
        assertThat(first.fullName()).isNotSameAs(first.fullName());
        assertThat(first.passwordHash()).isNotSameAs(first.passwordHash());
        assertThat(first).isNotEqualTo(new Object());
    }

    @Test
    void rejectsEveryMissingRequiredField() {
        Object[] fields = {
                new UserId(UUID.randomUUID()),
                new EmailAddress("user@example.com"),
                new PasswordHash("hash"),
                Role.PSYCHOLOGIST,
                new FullName("Clinic User"),
                Instant.parse("2026-05-26T10:00:00Z")
        };

        for (int index = 0; index < fields.length; index++) {
            Object[] invalid = fields.clone();
            invalid[index] = null;

            assertThatThrownBy(() -> User.rehydrate(
                    (UserId) invalid[0],
                    (EmailAddress) invalid[1],
                    (PasswordHash) invalid[2],
                    (Role) invalid[3],
                    (FullName) invalid[4],
                    true,
                    (Instant) invalid[5]))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User required fields are missing");
        }
    }
}
