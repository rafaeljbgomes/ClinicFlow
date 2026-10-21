package com.clinicflow.clinical.application.ports;

import com.clinicflow.clinical.domain.SessionRecordLinkConflict;

public interface SessionRecordLinkConflictRepository {
    void save(SessionRecordLinkConflict conflict);
}
