package com.clinicflow.auth.domain;

import java.time.Instant;
import java.util.Objects;

public final class User {
    private final UserId id;
    private final EmailAddress email;
    private final PasswordHash passwordHash;
    private final Role role;
    private final FullName fullName;
    private final boolean enabled;
    private final Instant createdAt;

    private User(UserId id, EmailAddress email, PasswordHash passwordHash, Role role,
                 FullName fullName, boolean enabled, Instant createdAt) {
        if (id == null || email == null || passwordHash == null || role == null
                || fullName == null || createdAt == null) {
            throw new IllegalArgumentException("User required fields are missing");
        }
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.fullName = fullName;
        this.enabled = enabled;
        this.createdAt = createdAt;
    }

    public static User create(UserId id, EmailAddress email, PasswordHash passwordHash, Role role,
                              FullName fullName, Instant createdAt) {
        return new User(id, email, passwordHash, role, fullName, true, createdAt);
    }

    public static User rehydrate(UserId id, EmailAddress email, PasswordHash passwordHash, Role role,
                                 FullName fullName, boolean enabled, Instant createdAt) {
        return new User(id, email, passwordHash, role, fullName, enabled, createdAt);
    }

    public UserId id() { return new UserId(id.value()); }
    public EmailAddress email() { return new EmailAddress(email.value()); }
    public PasswordHash passwordHash() { return new PasswordHash(passwordHash.value()); }
    public Role role() { return role; }
    public FullName fullName() { return new FullName(fullName.value()); }
    public boolean enabled() { return enabled; }
    public Instant createdAt() { return createdAt; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof User user && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
