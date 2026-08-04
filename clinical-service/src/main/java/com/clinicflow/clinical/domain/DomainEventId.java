package com.clinicflow.clinical.domain;

import java.util.UUID;

public record DomainEventId(UUID value) {
    public DomainEventId {
        if (value == null) {
            throw new IllegalArgumentException("Event id is required");
        }
    }
}
