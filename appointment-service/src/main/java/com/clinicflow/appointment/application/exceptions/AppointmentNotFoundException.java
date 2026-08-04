package com.clinicflow.appointment.application.exceptions;

public class AppointmentNotFoundException extends RuntimeException {
    public AppointmentNotFoundException() {
        super("Appointment not found");
    }
}
