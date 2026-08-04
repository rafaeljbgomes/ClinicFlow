package com.clinicflow.notification.application.mapping;

import com.clinicflow.notification.application.results.NotificationResult;
import com.clinicflow.notification.domain.Notification;
import org.mapstruct.Mapper;

@Mapper(config = MapperConfiguration.class)
public abstract class NotificationApplicationMapper {
    public abstract String copy(String value);

    public NotificationResult toResult(Notification notification) {
        return new NotificationResult(
                notification.id().value(),
                notification.eventId().value(),
                notification.eventType().value(),
                notification.psychologistId().map(value -> value.value()).orElse(null),
                notification.type(),
                notification.status(),
                notification.recipient().map(recipient -> recipient.value()).orElse(null),
                notification.subject().value(),
                notification.createdAt(),
                notification.sentAt()
        );
    }
}
