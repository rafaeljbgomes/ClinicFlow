package com.clinicflow.notification.application.commands;

import com.clinicflow.notification.domain.EventId;
import com.clinicflow.notification.domain.EventType;
import com.clinicflow.notification.domain.PsychologistId;
import com.clinicflow.notification.domain.RecipientAddress;

public record ProcessNotificationEventCommand(EventId eventId, EventType eventType,
                                              PsychologistId psychologistId, RecipientAddress recipient) {
    public ProcessNotificationEventCommand {
        if (eventId == null || eventType == null || psychologistId == null) {
            throw new IllegalArgumentException("Notification event fields are missing");
        }
    }
}
