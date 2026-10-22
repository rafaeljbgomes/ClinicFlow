package com.clinicflow.appointment.adapters.inbound.rest;

class InvalidPreconditionException extends RuntimeException {
    InvalidPreconditionException() {
        super("If-Match must be one strong entity tag");
    }
}
