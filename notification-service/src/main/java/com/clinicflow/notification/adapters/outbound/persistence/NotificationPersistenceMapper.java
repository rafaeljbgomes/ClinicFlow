package com.clinicflow.notification.adapters.outbound.persistence;

import com.clinicflow.notification.application.mapping.MapperConfiguration;
import com.clinicflow.notification.domain.EventId;
import com.clinicflow.notification.domain.EventType;
import com.clinicflow.notification.domain.FailureReason;
import com.clinicflow.notification.domain.Notification;
import com.clinicflow.notification.domain.NotificationId;
import com.clinicflow.notification.domain.NotificationMessage;
import com.clinicflow.notification.domain.NotificationSubject;
import com.clinicflow.notification.domain.RecipientAddress;
import com.clinicflow.notification.domain.PsychologistId;
import org.mapstruct.Mapper;

@Mapper(config = MapperConfiguration.class)
public abstract class NotificationPersistenceMapper {
    public abstract String copy(String value);

    public JpaNotificationEntity toJpa(Notification notification) {
        return new JpaNotificationEntity(
                notification.id().value(),
                notification.eventId().value(),
                notification.eventType().value(),
                notification.psychologistId().map(value -> value.value()).orElse(null),
                notification.type(),
                notification.status(),
                notification.recipient().map(recipient -> recipient.value()).orElse(null),
                notification.subject().value(),
                notification.message().value(),
                notification.failureReason().map(reason -> reason.value()).orElse(null),
                notification.createdAt(),
                notification.sentAt()
        );
    }

    public Notification toDomain(JpaNotificationEntity entity) {
        return Notification.rehydrate(
                new NotificationId(entity.id()),
                new EventId(entity.eventId()),
                new EventType(entity.eventType()),
                entity.psychologistId() == null ? null : new PsychologistId(entity.psychologistId()),
                entity.type(),
                entity.status(),
                entity.recipient() == null ? null : new RecipientAddress(entity.recipient()),
                new NotificationSubject(entity.subject()),
                new NotificationMessage(entity.message()),
                entity.failureReason() == null ? null : new FailureReason(entity.failureReason()),
                entity.createdAt(),
                entity.sentAt()
        );
    }
}
