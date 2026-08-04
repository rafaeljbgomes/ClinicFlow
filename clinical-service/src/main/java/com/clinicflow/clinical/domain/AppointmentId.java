package com.clinicflow.clinical.domain;

import java.util.UUID;

public record AppointmentId(UUID value) {
    public AppointmentId {
        if (value == null) {
            throw new IllegalArgumentException("Appointment id is required");
        }
    }
}
