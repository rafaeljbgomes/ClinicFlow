package com.clinicflow.appointment.application.exceptions;

public class AppointmentAccessDeniedException extends RuntimeException {
    public AppointmentAccessDeniedException() {
        super("Appointment access denied");
    }
}
