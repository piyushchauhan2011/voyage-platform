package com.voyage.app.rabbitmq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "application.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMqLabConfig {

    public static final String ROUTING_KEY_BOOKING_CONFIRM = "booking.confirm";
    public static final String ROUTING_KEY_EMAIL_SEND = "email.send";

    @Bean
    public MessageConverter rabbitLabMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public DirectExchange voyageJobsExchange(
            @Value("${application.rabbitmq.exchange}") String exchangeName) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public Queue bookingConfirmQueue(
            @Value("${application.rabbitmq.queues.booking-confirm}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Queue emailSendQueue(
            @Value("${application.rabbitmq.queues.email-send}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding bookingConfirmBinding(Queue bookingConfirmQueue, DirectExchange voyageJobsExchange) {
        return BindingBuilder.bind(bookingConfirmQueue)
                .to(voyageJobsExchange)
                .with(ROUTING_KEY_BOOKING_CONFIRM);
    }

    @Bean
    public Binding emailSendBinding(Queue emailSendQueue, DirectExchange voyageJobsExchange) {
        return BindingBuilder.bind(emailSendQueue)
                .to(voyageJobsExchange)
                .with(ROUTING_KEY_EMAIL_SEND);
    }
}
