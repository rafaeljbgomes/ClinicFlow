package com.clinicflow.notification.infrastructure.bootstrap;

import com.clinicflow.notification.application.ports.NotificationRepository;
import com.clinicflow.notification.domain.EventId;
import com.clinicflow.notification.domain.EventType;
import com.clinicflow.notification.domain.FailureReason;
import com.clinicflow.notification.domain.Notification;
import com.clinicflow.notification.domain.NotificationId;
import com.clinicflow.notification.domain.NotificationMessage;
import com.clinicflow.notification.domain.NotificationStatus;
import com.clinicflow.notification.domain.NotificationSubject;
import com.clinicflow.notification.domain.NotificationType;
import com.clinicflow.notification.domain.RecipientAddress;
import com.clinicflow.notification.domain.PsychologistId;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "clinicflow.demo.bootstrap", name = "enabled", havingValue = "true")
public class DemoDataBootstrap implements ApplicationRunner {
    private final NotificationRepository notifications;
    private final Clock clock;

    @Autowired
    public DemoDataBootstrap(NotificationRepository notifications) {
        this(notifications, Clock.systemUTC());
    }

    DemoDataBootstrap(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        for (DemoNotification notification : demoNotifications(now)) {
            notifications.save(Notification.rehydrate(
                    new NotificationId(notification.id()),
                    new EventId(notification.eventId()),
                    new EventType(notification.eventType()),
                    new PsychologistId(notification.psychologistId()),
                    notification.type(),
                    notification.status(),
                    notification.recipient() == null ? null : new RecipientAddress(notification.recipient()),
                    new NotificationSubject(notification.subject()),
                    new NotificationMessage(notification.message()),
                    notification.failureReason() == null ? null : new FailureReason(notification.failureReason()),
                    notification.createdAt(),
                    notification.sentAt()
            ));
        }
    }

    private static List<DemoNotification> demoNotifications(Instant now) {
        return List.of(
                new DemoNotification(uuid("40000000-0000-4000-8000-000000000001"),
                        uuid("41000000-0000-4000-8000-000000000001"), "PatientCreated", psychologistSofia(),
                        NotificationType.EMAIL, NotificationStatus.SENT,
                        "ana.martins@demo.clinicflow.local", "Bem-vinda ao ClinicFlow",
                        "O seu perfil foi criado pela equipa clinica.", null,
                        now.minus(20, ChronoUnit.DAYS), now.minus(20, ChronoUnit.DAYS)),
                new DemoNotification(uuid("40000000-0000-4000-8000-000000000002"),
                        uuid("41000000-0000-4000-8000-000000000002"), "AppointmentScheduled", psychologistSofia(),
                        NotificationType.EMAIL, NotificationStatus.SENT,
                        "joao.ferreira@demo.clinicflow.local", "Consulta agendada",
                        "A sua proxima consulta foi agendada.", null,
                        now.minus(10, ChronoUnit.DAYS), now.minus(10, ChronoUnit.DAYS)),
                new DemoNotification(uuid("40000000-0000-4000-8000-000000000003"),
                        uuid("41000000-0000-4000-8000-000000000003"), "AppointmentRescheduled", psychologistSofia(),
                        NotificationType.EMAIL, NotificationStatus.PENDING,
                        "joao.ferreira@demo.clinicflow.local", "Consulta reagendada",
                        "A consulta foi reagendada e aguarda confirmacao.", null,
                        now.minus(2, ChronoUnit.HOURS), null),
                new DemoNotification(uuid("40000000-0000-4000-8000-000000000004"),
                        uuid("41000000-0000-4000-8000-000000000004"), "AppointmentCancelled", psychologistMiguel(),
                        NotificationType.EMAIL, NotificationStatus.FAILED,
                        "ines.rocha@demo.clinicflow.local", "Consulta cancelada",
                        "A consulta foi cancelada conforme solicitado.",
                        "Simulated delivery failure for demo mailbox",
                        now.minus(5, ChronoUnit.DAYS), null),
                new DemoNotification(uuid("40000000-0000-4000-8000-000000000005"),
                        uuid("41000000-0000-4000-8000-000000000005"), "PatientCreated", psychologistSofia(),
                        NotificationType.SYSTEM, NotificationStatus.SENT,
                        null, "Novo paciente atribuido",
                        "Foi criado um paciente de demonstracao para a Dra. Sofia Almeida.", null,
                        now.minus(15, ChronoUnit.DAYS), now.minus(15, ChronoUnit.DAYS))
        );
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

    private static UUID psychologistSofia() {
        return uuid("00000000-0000-4000-8000-000000000011");
    }

    private static UUID psychologistMiguel() {
        return uuid("00000000-0000-4000-8000-000000000012");
    }

    private record DemoNotification(UUID id, UUID eventId, String eventType, UUID psychologistId,
                                    NotificationType type,
                                    NotificationStatus status, String recipient, String subject, String message,
                                    String failureReason, Instant createdAt, Instant sentAt) {
    }
}
