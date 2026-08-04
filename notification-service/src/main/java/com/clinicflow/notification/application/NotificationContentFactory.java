package com.clinicflow.notification.application;

import com.clinicflow.notification.domain.EventType;
import com.clinicflow.notification.domain.NotificationMessage;
import com.clinicflow.notification.domain.NotificationSubject;
import org.springframework.stereotype.Component;

@Component
public class NotificationContentFactory {
    public NotificationContent create(EventType eventType) {
        String subject = switch (eventType.value()) {
            case "PatientCreated" -> "Patient profile created";
            case "AppointmentScheduled" -> "Appointment scheduled";
            case "AppointmentRescheduled" -> "Appointment rescheduled";
            case "AppointmentCancelled" -> "Appointment cancelled";
            default -> "ClinicFlow notification";
        };
        return new NotificationContent(
                new NotificationSubject(subject),
                new NotificationMessage("A ClinicFlow event was processed: " + eventType.value())
        );
    }

    public record NotificationContent(NotificationSubject subject, NotificationMessage message) {
    }
}
