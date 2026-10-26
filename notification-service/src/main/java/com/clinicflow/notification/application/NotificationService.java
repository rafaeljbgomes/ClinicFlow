package com.clinicflow.notification.application;

import com.clinicflow.notification.application.commands.ProcessNotificationEventCommand;
import com.clinicflow.notification.application.exceptions.NotificationNotFoundException;
import com.clinicflow.notification.application.mapping.NotificationApplicationMapper;
import com.clinicflow.notification.application.ports.NotificationRepository;
import com.clinicflow.notification.application.queries.GetNotificationQuery;
import com.clinicflow.notification.application.queries.ListNotificationsQuery;
import com.clinicflow.notification.application.results.NotificationResult;
import com.clinicflow.notification.application.results.PageResult;
import com.clinicflow.notification.domain.Notification;
import com.clinicflow.notification.domain.NotificationId;
import com.clinicflow.notification.domain.PsychologistId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository notificationRepository;
    private final NotificationApplicationMapper mapper;
    private final NotificationContentFactory contentFactory;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationApplicationMapper mapper,
                               NotificationContentFactory contentFactory) {
        this.notificationRepository = notificationRepository;
        this.mapper = mapper;
        this.contentFactory = contentFactory;
    }

    @Transactional
    public void processEvent(ProcessNotificationEventCommand command) {
        if (notificationRepository.existsByEventId(command.eventId())) {
            log.info("notification_event_skipped_duplicate eventId={}", command.eventId().value());
            return;
        }
        NotificationContentFactory.NotificationContent content = contentFactory.create(command.eventType());
        Instant now = Instant.now();
        Notification notification = Notification.sent(
                new NotificationId(UUID.randomUUID()),
                command.eventId(),
                command.eventType(),
                command.psychologistId(),
                command.recipient(),
                content.subject(),
                content.message(),
                now
        );
        notificationRepository.save(notification);
        log.info("notification_sent notificationId={} eventId={} eventType={}",
                notification.id().value(), command.eventId().value(), command.eventType().value());
    }

    @Transactional(readOnly = true)
    public PageResult<NotificationResult> list(ListNotificationsQuery query) {
        return notificationRepository.findPage(query.statuses(), query.page()).map(mapper::toResult);
    }

    @Transactional(readOnly = true)
    public PageResult<NotificationResult> listForPsychologist(ListNotificationsQuery query,
                                                             PsychologistId psychologistId) {
        return notificationRepository.findPageByPsychologistId(psychologistId, query.statuses(), query.page())
                .map(mapper::toResult);
    }

    @Transactional(readOnly = true)
    public NotificationResult get(GetNotificationQuery query) {
        return notificationRepository.findById(query.notificationId()).map(mapper::toResult)
                .orElseThrow(NotificationNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public NotificationResult getForPsychologist(GetNotificationQuery query, PsychologistId psychologistId) {
        return notificationRepository.findByIdAndPsychologistId(query.notificationId(), psychologistId)
                .map(mapper::toResult).orElseThrow(NotificationNotFoundException::new);
    }
}
