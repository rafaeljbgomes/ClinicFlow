package com.clinicflow.clinical.domain;

public class SessionRecordNotSignableException extends RuntimeException {
    public SessionRecordNotSignableException() {
        super("Only draft session notes with a summary can be signed");
    }
}
