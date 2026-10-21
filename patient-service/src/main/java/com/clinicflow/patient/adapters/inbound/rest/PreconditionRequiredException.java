package com.clinicflow.patient.adapters.inbound.rest;

class PreconditionRequiredException extends RuntimeException {
    PreconditionRequiredException() {
        super("If-Match is required");
    }
}
