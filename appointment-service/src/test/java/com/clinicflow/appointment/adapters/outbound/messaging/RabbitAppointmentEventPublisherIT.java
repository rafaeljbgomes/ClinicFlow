package com.clinicflow.appointment.adapters.outbound.messaging;

import com.clinicflow.appointment.domain.AppointmentDate;
import com.clinicflow.appointment.domain.AppointmentId;
import com.clinicflow.appointment.domain.AppointmentType;
import com.clinicflow.appointment.domain.DomainEventId;
import com.clinicflow.appointment.domain.PatientId;
import com.clinicflow.appointment.domain.PsychologistId;
import com.clinicflow.appointment.domain.events.AppointmentCompletedEvent;
import com.clinicflow.appointment.domain.events.AppointmentScheduledEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
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
class RabbitAppointmentEventPublisherIT {
    private static final String EXCHANGE = "test.therapy.events";
    private static final String QUEUE = "test.appointment.scheduled";
    private static final String COMPLETED_QUEUE = "test.appointment.completed";

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
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        TopicExchange exchange = new TopicExchange(EXCHANGE, false, true);
        Queue queue = new Queue(QUEUE, false, true, true);
        Queue completedQueue = new Queue(COMPLETED_QUEUE, false, true, true);
        admin.declareExchange(exchange);
        admin.declareQueue(queue);
        admin.declareQueue(completedQueue);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("appointment.scheduled"));
        admin.declareBinding(BindingBuilder.bind(completedQueue).to(exchange).with("appointment.completed"));
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
        RabbitAppointmentEventPublisher publisher = new RabbitAppointmentEventPublisher(
                rabbitTemplate, EXCHANGE, new SimpleMeterRegistry(),
                Mappers.getMapper(AppointmentEventMapper.class));
        AppointmentScheduledEvent event = new AppointmentScheduledEvent(
                new DomainEventId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                "AppointmentScheduled",
                Instant.parse("2026-06-01T10:00:00Z"),
                new AppointmentId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")),
                new PatientId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")),
                new PsychologistId(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")),
                new AppointmentDate(Instant.parse("2099-07-01T10:00:00Z"))
        );

        publisher.publish(event);

        Message message = rabbitTemplate.receive(QUEUE, 5000);
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("appointment.scheduled");
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.eventId().value().toString());
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"AppointmentScheduled\"")
                .contains("\"appointmentId\":\"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\"");
    }

    @Test
    void publishesCompletedJsonContractToExpectedRoutingKey() {
        RabbitAppointmentEventPublisher publisher = new RabbitAppointmentEventPublisher(
                rabbitTemplate, EXCHANGE, new SimpleMeterRegistry(),
                Mappers.getMapper(AppointmentEventMapper.class));
        AppointmentCompletedEvent event = new AppointmentCompletedEvent(
                new DomainEventId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                "AppointmentCompleted",
                Instant.parse("2026-06-01T10:00:00Z"),
                new AppointmentId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")),
                new PatientId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")),
                new PsychologistId(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")),
                new AppointmentDate(Instant.parse("2099-07-01T10:00:00Z")),
                AppointmentType.ONLINE
        );

        publisher.publish(event);

        Message message = rabbitTemplate.receive(COMPLETED_QUEUE, 5000);
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("appointment.completed");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("\"eventType\":\"AppointmentCompleted\"")
                .contains("\"appointmentType\":\"ONLINE\"");
    }
}
