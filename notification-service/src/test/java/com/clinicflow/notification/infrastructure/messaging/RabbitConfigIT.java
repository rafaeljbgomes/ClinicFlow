package com.clinicflow.notification.infrastructure.messaging;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
            new RabbitMQContainer(org.testcontainers.utility.DockerImageName
                    .parse("rabbitmq:4.3.4-management-alpine@sha256:44bf7eb50fe1765885659e49ccfdc775f8e531964d979321aee380a071f49f94")
                    .asCompatibleSubstituteFor("rabbitmq"));

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

    @ParameterizedTest(name = "routes {0} to the notification queue")
    @ValueSource(strings = {
            "patient.created",
            "appointment.scheduled",
            "appointment.rescheduled",
            "appointment.cancelled"
    })
    void routesDeclaredDomainEventToNotificationQueue(String routingKey) {
        // Act
        rabbitTemplate.convertAndSend(EXCHANGE, routingKey, routingKey);

        // Assert
        Message message = rabbitTemplate.receive(QUEUE, 5000);
        assertThat(message).isNotNull();
        assertThat(body(message)).isEqualTo(routingKey);
    }

    @Test
    void routesNotificationFailuresToDlq() {
        // Act
        rabbitTemplate.convertAndSend(DLX, FAILED_ROUTING_KEY, "failed-event");

        // Assert
        Message message = rabbitTemplate.receive(DLQ, 5000);
        assertThat(message).isNotNull();
        assertThat(body(message)).isEqualTo("failed-event");
    }

    @Test
    void ignoresDeadLettersFromOtherServices() {
        // Act
        rabbitTemplate.convertAndSend(DLX, "clinical.failed", "clinical-failure");

        // Assert
        assertThat(rabbitTemplate.receive(DLQ, 1000)).isNull();
    }

    private String body(Message message) {
        return new String(message.getBody(), StandardCharsets.UTF_8);
    }
}
