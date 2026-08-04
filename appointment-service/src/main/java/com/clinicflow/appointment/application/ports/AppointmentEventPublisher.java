package com.clinicflow.appointment.application.ports;

import com.clinicflow.appointment.domain.events.AppointmentCancelledEvent;
import com.clinicflow.appointment.domain.events.AppointmentCompletedEvent;
import com.clinicflow.appointment.domain.events.AppointmentRescheduledEvent;
import com.clinicflow.appointment.domain.events.AppointmentScheduledEvent;

public interface AppointmentEventPublisher {
    void publish(AppointmentScheduledEvent event);
    void publish(AppointmentRescheduledEvent event);
    void publish(AppointmentCancelledEvent event);
    void publish(AppointmentCompletedEvent event);
}
