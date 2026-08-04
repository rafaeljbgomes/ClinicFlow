package com.clinicflow.patient.application.ports;

import com.clinicflow.patient.domain.events.PatientCreatedEvent;

public interface PatientEventPublisher {
    void publish(PatientCreatedEvent event);
}
