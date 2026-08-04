package com.clinicflow.auth.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public record EmailAddress(String value) {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public EmailAddress {
        value = value == null ? null : value.trim().toLowerCase(Locale.ROOT);
        if (value == null || value.length() > 320 || !EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid email address");
        }
    }
}
