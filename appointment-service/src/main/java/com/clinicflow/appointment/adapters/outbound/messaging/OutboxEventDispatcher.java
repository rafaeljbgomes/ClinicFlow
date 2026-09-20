package com.clinicflow.appointment.adapters.outbound.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxEventDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxEventDispatcher.class);

    private final SpringDataOutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final OutboxProperties properties;
    private final OutboxMetrics metrics;

    public OutboxEventDispatcher(SpringDataOutboxEventRepository repository,
                                 RabbitTemplate rabbitTemplate,
                                 @Value("${clinicflow.messaging.exchange}") String exchange,
                                 OutboxProperties properties,
                                 OutboxMetrics metrics) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean dispatchNext() {
        return repository.lockNextDue(Instant.now()).map(event -> {
            try {
                publish(event);
                event.markPublished(Instant.now());
                metrics.recordPublication(event.eventType(), "success");
                log.info("outbox_event_published eventId={} routingKey={} retryCount={}",
                        event.eventId(), event.routingKey(), event.retryCount());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                recordFailure(event, exception);
            } catch (Exception exception) {
                recordFailure(event, exception);
            }
            return true;
        }).orElse(false);
    }

    @Transactional
    public int cleanupPublished() {
        int deleted = repository.deletePublishedBefore(Instant.now().minus(properties.publishedRetention()));
        if (deleted > 0) {
            log.info("outbox_events_cleaned count={}", deleted);
        }
        return deleted;
    }

    private void publish(JpaOutboxEvent event) throws Exception {
        CorrelationData correlationData = new CorrelationData(event.eventId().toString());
        rabbitTemplate.send(exchange, event.routingKey(), message(event), correlationData);
        CorrelationData.Confirm confirm = correlationData.getFuture().get(
                properties.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
        if (!confirm.isAck()) {
            throw new IllegalStateException("RabbitMQ negatively acknowledged the event: " + confirm.getReason());
        }
        if (correlationData.getReturned() != null) {
            throw new IllegalStateException("RabbitMQ returned the event as unroutable: "
                    + correlationData.getReturned().getReplyText());
        }
    }

    private Message message(JpaOutboxEvent event) {
        var builder = MessageBuilder
                .withBody(event.payload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(event.eventId().toString())
                .setType(event.eventType());
        if (event.correlationId() != null) {
            builder.setHeader("X-Correlation-Id", event.correlationId());
        }
        return builder.build();
    }

    private void recordFailure(JpaOutboxEvent event, Exception exception) {
        Duration delay = retryDelay(event.retryCount());
        event.markFailed(exception.getMessage(), Instant.now().plus(delay));
        metrics.recordPublication(event.eventType(), "failure");
        log.warn("outbox_event_failed eventId={} routingKey={} retryCount={} nextAttemptAt={} error={}",
                event.eventId(), event.routingKey(), event.retryCount(), event.nextAttemptAt(), event.lastError());
    }

    private Duration retryDelay(int retryCount) {
        int exponent = Math.min(retryCount, 30);
        long multiplier = 1L << exponent;
        Duration candidate;
        try {
            candidate = properties.retryInitial().multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            return properties.retryMax();
        }
        return candidate.compareTo(properties.retryMax()) > 0 ? properties.retryMax() : candidate;
    }
}
