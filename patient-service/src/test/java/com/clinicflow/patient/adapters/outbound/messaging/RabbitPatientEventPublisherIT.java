package com.clinicflow.patient.adapters.outbound.messaging;

import com.clinicflow.patient.domain.EmailAddress;
import com.clinicflow.patient.domain.PatientId;
import com.clinicflow.patient.domain.PsychologistId;
import com.clinicflow.patient.domain.events.PatientCreatedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.slf4j.MDC;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RabbitPatientEventPublisherIT {
    private static final String EXCHANGE = "test.therapy.events";
    private static final String QUEUE = "test.patient.created";

    @Container
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer("rabbitmq:4.1-alpine");

    private static CachingConnectionFactory connectionFactory;
    private static RabbitTemplate rabbitTemplate;

    @BeforeAll
    static void configureBroker() {
        connectionFactory = new CachingConnectionFactory(RABBITMQ.getHost(), RABBITMQ.getAmqpPort());
        connectionFactory.setUsername(RABBITMQ.getAdminUsername());
        connectionFactory.setPassword(RABBITMQ.getAdminPassword());
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        TopicExchange exchange = new TopicExchange(EXCHANGE, false, true);
        Queue queue = new Queue(QUEUE, false, false, true);
        admin.declareExchange(exchange);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("patient.created"));
        rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
    }

    @AfterAll
    static void closeConnection() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void publishesJsonContractToExpectedRoutingKey() {
        RabbitPatientEventPublisher publisher = new RabbitPatientEventPublisher(
                rabbitTemplate, EXCHANGE, new SimpleMeterRegistry(),
                Mappers.getMapper(PatientEventMapper.class));
        PatientCreatedEvent event = new PatientCreatedEvent(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "PatientCreated",
                Instant.parse("2026-06-01T10:00:00Z"),
                new PatientId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")),
                new PsychologistId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")),
                new EmailAddress("patient@example.com")
        );
        MDC.put("correlation.id", "correlation-123");

        try {
            publisher.publish(event);
        } finally {
            MDC.clear();
        }

        Message message = rabbitTemplate.receive(QUEUE, 5000);
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("patient.created");
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.eventId().toString());
        assertThat(message.getMessageProperties().getHeader("X-Correlation-Id").toString())
                .isEqualTo("correlation-123");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"PatientCreated\"")
                .contains("\"patientEmail\":\"patient@example.com\"");
    }
}
