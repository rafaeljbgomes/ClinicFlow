package com.clinicflow.notification.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public record RecipientAddress(String value) {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public RecipientAddress {
        value = value == null ? null : value.trim().toLowerCase(Locale.ROOT);
        if (value == null || value.length() > 320 || !EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Recipient address is invalid");
        }
    }
}
