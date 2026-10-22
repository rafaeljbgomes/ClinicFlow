package com.clinicflow.appointment.adapters.inbound.rest;

class PreconditionRequiredException extends RuntimeException {
    PreconditionRequiredException() {
        super("If-Match is required");
    }
}
