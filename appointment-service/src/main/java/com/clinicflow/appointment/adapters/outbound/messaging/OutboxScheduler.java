package com.clinicflow.appointment.adapters.outbound.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "clinicflow.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
class OutboxScheduler {
    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final OutboxEventDispatcher dispatcher;
    private final OutboxProperties properties;
    private final OutboxMetrics metrics;

    OutboxScheduler(OutboxEventDispatcher dispatcher, OutboxProperties properties, OutboxMetrics metrics) {
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${clinicflow.outbox.poll-interval:1s}")
    void dispatch() {
        try {
            for (int processed = 0; processed < properties.batchSize(); processed++) {
                if (!dispatcher.dispatchNext()) {
                    break;
                }
            }
            metrics.refreshBacklog();
        } catch (RuntimeException exception) {
            log.error("outbox_dispatch_cycle_failed", exception);
        }
    }

    @Scheduled(fixedDelayString = "${clinicflow.outbox.cleanup-interval:1h}")
    void cleanup() {
        try {
            dispatcher.cleanupPublished();
            metrics.refreshBacklog();
        } catch (RuntimeException exception) {
            log.error("outbox_cleanup_cycle_failed", exception);
        }
    }
}
