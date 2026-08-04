package com.clinicflow.appointment.domain;

import java.util.UUID;

public record DomainEventId(UUID value) {
    public DomainEventId {
        if (value == null) {
            throw new IllegalArgumentException("Domain event id is required");
        }
    }
}
