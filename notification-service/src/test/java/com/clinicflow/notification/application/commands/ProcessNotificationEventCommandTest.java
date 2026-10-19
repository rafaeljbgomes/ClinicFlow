package com.clinicflow.notification.application.commands;

import com.clinicflow.notification.domain.EventId;
import com.clinicflow.notification.domain.EventType;
import com.clinicflow.notification.domain.PsychologistId;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ProcessNotificationEventCommandTest {
    static Stream<Arguments> missingFields() {
        EventId eventId = new EventId(UUID.randomUUID());
        EventType eventType = new EventType("PatientCreated");
        PsychologistId psychologistId = new PsychologistId(UUID.randomUUID());
        return Stream.of(
                Arguments.of("event id", null, eventType, psychologistId),
                Arguments.of("event type", eventId, null, psychologistId),
                Arguments.of("psychologist id", eventId, eventType, null));
    }

    @ParameterizedTest(name = "rejects missing {0}")
    @MethodSource("missingFields")
    void rejectsMissingRequiredField(String field, EventId eventId, EventType eventType,
                                     PsychologistId psychologistId) {
        // Act
        Throwable thrown = catchThrowable(() ->
                new ProcessNotificationEventCommand(eventId, eventType, psychologistId, null));

        // Assert
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Notification event fields are missing");
    }
}
