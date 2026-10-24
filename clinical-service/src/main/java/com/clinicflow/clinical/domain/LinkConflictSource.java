package com.clinicflow.clinical.domain;

// Where the appointment's real owner was learned from when a conflicting link was released.
public enum LinkConflictSource {
    APPOINTMENT_COMPLETED,
    MANUAL_LINK
}
