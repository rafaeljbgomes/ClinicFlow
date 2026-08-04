package com.clinicflow.clinical.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration
@EnableConfigurationProperties(MessagingRetryProperties.class)
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
    Queue clinicalQueue(@Value("${clinicflow.messaging.clinical-queue}") String queue,
                        @Value("${clinicflow.messaging.dead-letter-exchange}") String deadLetterExchange,
                        @Value("${clinicflow.messaging.dead-letter-routing-key}") String deadLetterRoutingKey) {
        return QueueBuilder.durable(queue)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(deadLetterRoutingKey)
                .build();
    }

    @Bean
    Queue clinicalDlq(@Value("${clinicflow.messaging.clinical-dlq}") String queue) {
        return QueueBuilder.durable(queue).build();
    }

    @Bean
    Binding clinicalCompletedBinding(Queue clinicalQueue, TopicExchange clinicflowExchange) {
        return BindingBuilder.bind(clinicalQueue).to(clinicflowExchange).with("appointment.completed");
    }

    @Bean
    Binding clinicalDeadLetterBinding(Queue clinicalDlq,
                                      TopicExchange deadLetterExchange,
                                      @Value("${clinicflow.messaging.dead-letter-routing-key}") String routingKey) {
        return BindingBuilder.bind(clinicalDlq).to(deadLetterExchange).with(routingKey);
    }

    @Bean
    MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    MessageRecoverer messageRecoverer() {
        return new SafeRejectAndDontRequeueRecoverer("clinical-service");
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                        MessageConverter messageConverter,
                                                                        MessagingRetryProperties retry,
                                                                        MessageRecoverer messageRecoverer,
                                                                        @Value("${spring.rabbitmq.listener.simple.auto-startup:true}")
                                                                        boolean autoStartup) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAutoStartup(autoStartup);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(retry.maxAttempts())
                .backOffOptions(retry.initialInterval().toMillis(), retry.multiplier(), retry.maxInterval().toMillis())
                .recoverer(messageRecoverer)
                .build());
        return factory;
    }
}
