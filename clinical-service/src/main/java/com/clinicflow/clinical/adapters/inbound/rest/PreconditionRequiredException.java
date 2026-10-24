package com.clinicflow.clinical.adapters.inbound.rest;

class PreconditionRequiredException extends RuntimeException {
    PreconditionRequiredException() {
        super("If-Match is required");
    }
}
