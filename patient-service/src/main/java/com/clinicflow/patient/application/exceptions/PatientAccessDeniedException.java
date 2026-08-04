package com.clinicflow.patient.application.exceptions;

public class PatientAccessDeniedException extends RuntimeException {
    public PatientAccessDeniedException() {
        super("Patient access denied");
    }
}
