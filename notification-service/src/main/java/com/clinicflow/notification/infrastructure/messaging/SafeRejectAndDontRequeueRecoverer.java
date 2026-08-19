package com.clinicflow.notification.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;

final class SafeRejectAndDontRequeueRecoverer implements MessageRecoverer {
    private static final Logger log = LoggerFactory.getLogger(SafeRejectAndDontRequeueRecoverer.class);
    private final String service;

    SafeRejectAndDontRequeueRecoverer(String service) {
        this.service = service;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        MessageProperties properties = message.getMessageProperties();
        log.error(
                "consumer_retry_exhausted service={} routing_key={} message_id={} correlation_id={} cause_type={}",
                service,
                properties.getReceivedRoutingKey(),
                properties.getMessageId(),
                properties.getCorrelationId(),
                cause.getClass().getSimpleName());
        throw new AmqpRejectAndDontRequeueException("Consumer retry exhausted", cause);
    }
}
