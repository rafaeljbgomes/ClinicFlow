package com.clinicflow.patient.domain;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

public record PhoneNumber(String value) {
    private static final PhoneNumberUtil PHONE_UTIL = PhoneNumberUtil.getInstance();

    public PhoneNumber {
        value = normalize(value);
    }

    private static String normalize(String rawValue) {
        if (rawValue == null || !rawValue.trim().startsWith("+")) {
            throw new IllegalArgumentException("Phone number must use international E.164 format");
        }
        try {
            Phonenumber.PhoneNumber parsed = PHONE_UTIL.parse(rawValue.trim(), "ZZ");
            if (!PHONE_UTIL.isValidNumber(parsed)) {
                throw new IllegalArgumentException("Phone number is invalid");
            }
            return PHONE_UTIL.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException ex) {
            throw new IllegalArgumentException("Phone number is invalid", ex);
        }
    }
}
