package com.clinicflow.patient.adapters.inbound.rest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Aggregate versions travel as strong entity tags such as "3" (ADR-024).
final class EntityTags {
    private static final Pattern VERSION_TAG = Pattern.compile("\"(\\d{1,18})\"");

    private EntityTags() {
    }

    static String of(long version) {
        return "\"" + version + "\"";
    }

    // Checked before any lookup, so a missing or malformed header gets the same answer for every id.
    static long requireIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException();
        }
        Matcher matcher = VERSION_TAG.matcher(ifMatch.strip());
        if (!matcher.matches()) {
            throw new InvalidPreconditionException();
        }
        return Long.parseLong(matcher.group(1));
    }
}
