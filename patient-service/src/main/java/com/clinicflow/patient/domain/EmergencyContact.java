package com.clinicflow.patient.domain;

public record EmergencyContact(String name, PhoneNumber phone, String relationship) {
    public EmergencyContact {
        if (name == null || name.isBlank() || phone == null) {
            throw new IllegalArgumentException("Emergency contact name and phone are required");
        }
        name = normalize(name, 160, "Emergency contact name is invalid");
        relationship = relationship == null || relationship.isBlank()
                ? null : normalize(relationship, 80, "Emergency contact relationship is invalid");
    }

    private static String normalize(String value, int maxLength, String message) {
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
