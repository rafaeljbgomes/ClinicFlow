package com.clinicflow.appointment.domain;

import java.time.Instant;

public record AppointmentDate(Instant value) {
    public AppointmentDate {
        if (value == null) {
            throw new IllegalArgumentException("Appointment date is required");
        }
    }

    public void requireFutureFrom(Instant now) {
        if (now == null || !value.isAfter(now)) {
            throw new IllegalArgumentException("Appointment date must be in the future");
        }
    }
}
