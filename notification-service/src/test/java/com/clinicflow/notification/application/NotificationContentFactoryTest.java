package com.clinicflow.notification.application;

import com.clinicflow.notification.domain.EventType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationContentFactoryTest {
    private final NotificationContentFactory factory = new NotificationContentFactory();

    @ParameterizedTest(name = "{0} produces subject \"{1}\"")
    @CsvSource({
            "PatientCreated,Patient profile created",
            "AppointmentScheduled,Appointment scheduled",
            "AppointmentRescheduled,Appointment rescheduled",
            "AppointmentCancelled,Appointment cancelled",
            "UnexpectedEvent,ClinicFlow notification"
    })
    void createsExpectedContentForEverySupportedEvent(String eventType, String subject) {
        // Arrange
        EventType type = new EventType(eventType);

        // Act
        var content = factory.create(type);

        // Assert
        assertThat(content.subject().value()).isEqualTo(subject);
        assertThat(content.message().value()).contains(eventType);
    }
}
