package com.clinicflow.clinical.application.exceptions;

public class StaleResourceVersionException extends RuntimeException {
    public StaleResourceVersionException() {
        super("Resource changed since the requested version");
    }
}
