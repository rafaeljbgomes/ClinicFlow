package com.clinicflow.clinical.domain;

import java.util.UUID;

public record SessionRecordId(UUID value) {
    public SessionRecordId {
        if (value == null) {
            throw new IllegalArgumentException("Session record id is required");
        }
    }
}
