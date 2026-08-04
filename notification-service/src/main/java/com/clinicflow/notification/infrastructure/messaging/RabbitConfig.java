package com.clinicflow.notification.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    @Bean
    TopicExchange clinicflowExchange(@Value("${clinicflow.messaging.exchange}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    TopicExchange deadLetterExchange(@Value("${clinicflow.messaging.dead-letter-exchange}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    Queue notificationQueue(@Value("${clinicflow.messaging.notification-queue}") String queue,
                            @Value("${clinicflow.messaging.dead-letter-exchange}") String deadLetterExchange) {
        return QueueBuilder.durable(queue).deadLetterExchange(deadLetterExchange).build();
    }

    @Bean
    Queue notificationDlq(@Value("${clinicflow.messaging.notification-dlq}") String queue) {
        return QueueBuilder.durable(queue).build();
    }

    @Bean
    Declarables notificationBindings(Queue notificationQueue, TopicExchange clinicflowExchange) {
        return new Declarables(
                BindingBuilder.bind(notificationQueue).to(clinicflowExchange).with("patient.created"),
                BindingBuilder.bind(notificationQueue).to(clinicflowExchange).with("appointment.scheduled"),
                BindingBuilder.bind(notificationQueue).to(clinicflowExchange).with("appointment.rescheduled"),
                BindingBuilder.bind(notificationQueue).to(clinicflowExchange).with("appointment.cancelled")
        );
    }

    @Bean
    Binding deadLetterBinding(Queue notificationDlq, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(notificationDlq).to(deadLetterExchange).with("#");
    }

    @Bean
    MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAutoStartup(autoStartup);
        return factory;
    }
}
