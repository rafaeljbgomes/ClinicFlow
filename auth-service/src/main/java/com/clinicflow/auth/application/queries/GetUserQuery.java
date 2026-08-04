package com.clinicflow.auth.application.queries;

import com.clinicflow.auth.domain.UserId;

public record GetUserQuery(UserId userId) {
    public GetUserQuery {
        if (userId == null) {
            throw new IllegalArgumentException("User id is required");
        }
    }
}
