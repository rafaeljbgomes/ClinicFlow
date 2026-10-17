package com.clinicflow.patient.adapters.outbound.messaging;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
class OutboxMetrics {
    private final SpringDataOutboxEventRepository repository;
    private final MeterRegistry meterRegistry;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    OutboxMetrics(SpringDataOutboxEventRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
        Gauge.builder("clinicflow.outbox.events", pending, AtomicLong::get)
                .tag("service", "patient-service")
                .tag("status", "pending")
                .register(meterRegistry);
        Gauge.builder("clinicflow.outbox.events", failed, AtomicLong::get)
                .tag("service", "patient-service")
                .tag("status", "failed")
                .register(meterRegistry);
    }

    void recordPublication(String eventType, String status) {
        meterRegistry.counter(
                "clinicflow.domain.events.published",
                "service", "patient-service",
                "event_type", eventType,
                "status", status
        ).increment();
    }

    void refreshBacklog() {
        pending.set(repository.countByStatus(OutboxEventStatus.PENDING));
        failed.set(repository.countByStatus(OutboxEventStatus.FAILED));
    }
}
