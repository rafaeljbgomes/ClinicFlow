package com.clinicflow.patient.adapters.inbound.rest;

class InvalidPreconditionException extends RuntimeException {
    InvalidPreconditionException() {
        super("If-Match must be one strong entity tag");
    }
}
