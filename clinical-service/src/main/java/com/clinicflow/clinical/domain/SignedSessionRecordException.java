package com.clinicflow.clinical.domain;

public class SignedSessionRecordException extends RuntimeException {
    public SignedSessionRecordException() {
        super("Signed session records cannot be changed");
    }
}
