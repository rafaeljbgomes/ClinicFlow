package com.clinicflow.clinical.domain;

import java.util.UUID;

public record SessionRecordLinkConflictId(UUID value) {
    public SessionRecordLinkConflictId {
        if (value == null) {
            throw new IllegalArgumentException("Session record link conflict id is required");
        }
    }
}
