package com.clinicflow.appointment.application.exceptions;

public class OwnershipCheckUnavailableException extends RuntimeException {
    public OwnershipCheckUnavailableException(String message) {
        super(message);
    }

    public OwnershipCheckUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
