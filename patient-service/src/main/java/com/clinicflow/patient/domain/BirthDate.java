package com.clinicflow.patient.domain;

import java.time.LocalDate;

public record BirthDate(LocalDate value) {
    private static final LocalDate EARLIEST_ALLOWED = LocalDate.of(1900, 1, 1);

    public BirthDate {
        if (value == null) {
            throw new IllegalArgumentException("Birth date is required");
        }
        if (value.isBefore(EARLIEST_ALLOWED) || value.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Birth date is invalid");
        }
    }
}
