package com.clinicflow.clinical.infrastructure.messaging;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RabbitConfigIT {
    private static final String EXCHANGE = "test.therapy.events";
    private static final String DLX = "test.therapy.dlx";
    private static final String QUEUE = "test.clinical.queue";
    private static final String DLQ = "test.clinical.dlq";

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
        Queue queue = config.clinicalQueue(QUEUE, DLX);
        Queue dlq = config.clinicalDlq(DLQ);
        Binding completed = config.clinicalCompletedBinding(queue, exchange);
        Binding deadLetter = config.clinicalDeadLetterBinding(dlq, dlx);
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.declareExchange(exchange);
        admin.declareExchange(dlx);
        admin.declareQueue(queue);
        admin.declareQueue(dlq);
        admin.declareBinding(completed);
        admin.declareBinding(deadLetter);
        rabbitTemplate = new RabbitTemplate(connectionFactory);
    }

    @AfterAll
    static void closeConnection() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void routesCompletedAppointmentEventsToClinicalQueue() {
        rabbitTemplate.convertAndSend(EXCHANGE, "appointment.completed", "completed");

        Object message = rabbitTemplate.receiveAndConvert(QUEUE, 5000);

        assertThat(message).isEqualTo("completed");
    }
}
