package com.clinicflow.clinical.domain;

public class InvalidNoteTransitionException extends RuntimeException {
    public InvalidNoteTransitionException() {
        super("Session notes can only be signed through the sign action");
    }
}
