package com.clinicflow.notification.domain;

import java.util.UUID;

public record EventId(UUID value) {
    public EventId {
        if (value == null) {
            throw new IllegalArgumentException("Event id is required");
        }
    }
}
