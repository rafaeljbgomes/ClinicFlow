package com.clinicflow.notification.infrastructure.messaging;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RabbitConfigIT {
    private static final String EXCHANGE = "test.therapy.events";
    private static final String DLX = "test.therapy.events.dlx";
    private static final String QUEUE = "test.notification.events";
    private static final String DLQ = "test.notification.events.dlq";
    private static final String FAILED_ROUTING_KEY = "notification.failed";

    @Container
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer("rabbitmq:4.3.4-alpine");

    private static CachingConnectionFactory connectionFactory;
    private static RabbitTemplate rabbitTemplate;

    @BeforeAll
    static void configureBroker() {
        connectionFactory = new CachingConnectionFactory(RABBITMQ.getHost(), RABBITMQ.getAmqpPort());
        connectionFactory.setUsername(RABBITMQ.getAdminUsername());
        connectionFactory.setPassword(RABBITMQ.getAdminPassword());
        RabbitConfig config = new RabbitConfig();
        TopicExchange exchange = config.clinicflowExchange(EXCHANGE);
        TopicExchange dlx = config.deadLetterExchange(DLX);
        Queue queue = config.notificationQueue(QUEUE, DLX, FAILED_ROUTING_KEY);
        Queue dlq = config.notificationDlq(DLQ);
        Declarables bindings = config.notificationBindings(queue, exchange);
        Binding deadLetterBinding = config.deadLetterBinding(dlq, dlx, FAILED_ROUTING_KEY);
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.declareExchange(exchange);
        admin.declareExchange(dlx);
        admin.declareQueue(queue);
        admin.declareQueue(dlq);
        bindings.getDeclarablesByType(Binding.class).forEach(admin::declareBinding);
        admin.declareBinding(deadLetterBinding);
        rabbitTemplate = new RabbitTemplate(connectionFactory);
    }

    @AfterAll
    static void closeConnection() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void routesAllDeclaredDomainEventsToNotificationQueue() {
        for (String routingKey : new String[]{
                "patient.created",
                "appointment.scheduled",
                "appointment.rescheduled",
                "appointment.cancelled"
        }) {
            rabbitTemplate.convertAndSend(EXCHANGE, routingKey, routingKey);
        }

        assertThat(receivedBodies(4))
                .containsExactlyInAnyOrder(
                        "patient.created",
                        "appointment.scheduled",
                        "appointment.rescheduled",
                        "appointment.cancelled"
                );
    }

    @Test
    void routesDeadLettersToDlq() {
        rabbitTemplate.convertAndSend(DLX, FAILED_ROUTING_KEY, "failed-event");
        rabbitTemplate.convertAndSend(DLX, "clinical.failed", "clinical-failure");

        Message message = rabbitTemplate.receive(DLQ, 5000);
        assertThat(message).isNotNull();
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo("failed-event");
        assertThat(rabbitTemplate.receive(DLQ, 250)).isNull();
    }

    private java.util.List<String> receivedBodies(int count) {
        java.util.List<String> bodies = new java.util.ArrayList<>();
        for (int index = 0; index < count; index++) {
            Message message = rabbitTemplate.receive(QUEUE, 5000);
            assertThat(message).isNotNull();
            bodies.add(new String(message.getBody(), StandardCharsets.UTF_8));
        }
        return bodies;
    }
}
