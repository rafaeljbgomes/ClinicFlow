package com.clinicflow.patient.application.exceptions;

public class PatientNotFoundException extends RuntimeException {
    public PatientNotFoundException() {
        super("Patient not found");
    }
}
