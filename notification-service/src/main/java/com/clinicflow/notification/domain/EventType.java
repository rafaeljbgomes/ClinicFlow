package com.clinicflow.notification.domain;

import java.util.regex.Pattern;

public record EventType(String value) {
    private static final Pattern VALID_TYPE = Pattern.compile("^[A-Za-z][A-Za-z0-9._-]{0,79}$");

    public EventType {
        value = value == null ? null : value.trim();
        if (value == null || !VALID_TYPE.matcher(value).matches()) {
            throw new IllegalArgumentException("Event type is invalid");
        }
    }
}
