package com.clinicflow.auth.application.exceptions;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException() {
        super("Email is already registered");
    }
}
