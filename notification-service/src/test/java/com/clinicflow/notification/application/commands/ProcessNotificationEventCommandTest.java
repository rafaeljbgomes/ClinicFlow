package com.clinicflow.notification.application.commands;

import com.clinicflow.notification.domain.EventId;
import com.clinicflow.notification.domain.EventType;
import com.clinicflow.notification.domain.PsychologistId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessNotificationEventCommandTest {
    @Test
    void rejectsEveryMissingRequiredField() {
        EventId eventId = new EventId(UUID.randomUUID());
        EventType eventType = new EventType("PatientCreated");
        PsychologistId psychologistId = new PsychologistId(UUID.randomUUID());

        assertThatThrownBy(() -> new ProcessNotificationEventCommand(null, eventType, psychologistId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notification event fields are missing");
        assertThatThrownBy(() -> new ProcessNotificationEventCommand(eventId, null, psychologistId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notification event fields are missing");
        assertThatThrownBy(() -> new ProcessNotificationEventCommand(eventId, eventType, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notification event fields are missing");
    }
}
