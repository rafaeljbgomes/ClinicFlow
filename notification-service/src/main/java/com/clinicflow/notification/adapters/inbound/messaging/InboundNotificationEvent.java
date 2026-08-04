package com.clinicflow.notification.adapters.inbound.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InboundNotificationEvent(UUID eventId, String eventType, UUID psychologistId, String patientEmail) {
}
