package com.clinicflow.auth.domain;

import java.util.UUID;

public record UserId(UUID value) {
    public UserId {
        if (value == null) {
            throw new IllegalArgumentException("User id is required");
        }
    }
}
