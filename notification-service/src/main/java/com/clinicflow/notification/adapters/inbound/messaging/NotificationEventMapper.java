package com.clinicflow.notification.adapters.inbound.messaging;

import com.clinicflow.notification.application.commands.ProcessNotificationEventCommand;
import com.clinicflow.notification.application.mapping.MapperConfiguration;
import com.clinicflow.notification.domain.EventId;
import com.clinicflow.notification.domain.EventType;
import com.clinicflow.notification.domain.RecipientAddress;
import com.clinicflow.notification.domain.PsychologistId;
import org.mapstruct.Mapper;

@Mapper(config = MapperConfiguration.class)
public abstract class NotificationEventMapper {
    public abstract String copy(String value);

    public ProcessNotificationEventCommand toCommand(InboundNotificationEvent event) {
        return new ProcessNotificationEventCommand(
                new EventId(event.eventId()),
                new EventType(event.eventType()),
                new PsychologistId(event.psychologistId()),
                event.patientEmail() == null || event.patientEmail().isBlank()
                        ? null : new RecipientAddress(event.patientEmail())
        );
    }
}
