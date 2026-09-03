package com.jarvis.commerce.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static com.jarvis.commerce.messaging.RabbitTopology.PAYMENT_COMMAND_EXCHANGE;
import static com.jarvis.commerce.messaging.RabbitTopology.PAYMENT_DEAD_LETTER_EXCHANGE;
import static com.jarvis.commerce.messaging.RabbitTopology.PAYMENT_DEAD_LETTER_KEY;
import static com.jarvis.commerce.messaging.RabbitTopology.PAYMENT_DEAD_LETTER_QUEUE;
import static com.jarvis.commerce.messaging.RabbitTopology.PAYMENT_EVENT_EXCHANGE;
import static com.jarvis.commerce.messaging.RabbitTopology.PAYMENT_TIMEOUT_DELAY_QUEUE;
import static com.jarvis.commerce.messaging.RabbitTopology.PAYMENT_TIMEOUT_DUE_KEY;
import static com.jarvis.commerce.messaging.RabbitTopology.PAYMENT_TIMEOUT_QUEUE;
import static com.jarvis.commerce.messaging.RabbitTopology.PAYMENT_TIMEOUT_SCHEDULE_KEY;
import static com.jarvis.commerce.messaging.RabbitTopology.REFUND_COMMAND_EXCHANGE;
import static com.jarvis.commerce.messaging.RabbitTopology.REFUND_DEAD_LETTER_EXCHANGE;
import static com.jarvis.commerce.messaging.RabbitTopology.REFUND_DEAD_LETTER_KEY;
import static com.jarvis.commerce.messaging.RabbitTopology.REFUND_DEAD_LETTER_QUEUE;
import static com.jarvis.commerce.messaging.RabbitTopology.REFUND_REQUEST_KEY;
import static com.jarvis.commerce.messaging.RabbitTopology.REFUND_REQUEST_QUEUE;

@Configuration
@ConditionalOnProperty(name = "commerce.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMqConfiguration {

    @Bean
    MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    DirectExchange paymentCommandExchange() {
        return new DirectExchange(PAYMENT_COMMAND_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange paymentEventExchange() {
        return new DirectExchange(PAYMENT_EVENT_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange paymentDeadLetterExchange() {
        return new DirectExchange(PAYMENT_DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue paymentTimeoutDelayQueue(@Value("${commerce.payment.timeout:PT15M}") Duration timeout) {
        return QueueBuilder.durable(PAYMENT_TIMEOUT_DELAY_QUEUE)
                .ttl(Math.toIntExact(timeout.toMillis()))
                .deadLetterExchange(PAYMENT_EVENT_EXCHANGE)
                .deadLetterRoutingKey(PAYMENT_TIMEOUT_DUE_KEY)
                .build();
    }

    @Bean
    Binding paymentTimeoutDelayBinding(Queue paymentTimeoutDelayQueue,
                                       DirectExchange paymentCommandExchange) {
        return BindingBuilder.bind(paymentTimeoutDelayQueue)
                .to(paymentCommandExchange)
                .with(PAYMENT_TIMEOUT_SCHEDULE_KEY);
    }

    @Bean
    Queue paymentTimeoutQueue() {
        return QueueBuilder.durable(PAYMENT_TIMEOUT_QUEUE)
                .deadLetterExchange(PAYMENT_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(PAYMENT_DEAD_LETTER_KEY)
                .build();
    }

    @Bean
    Binding paymentTimeoutBinding(Queue paymentTimeoutQueue, DirectExchange paymentEventExchange) {
        return BindingBuilder.bind(paymentTimeoutQueue)
                .to(paymentEventExchange)
                .with(PAYMENT_TIMEOUT_DUE_KEY);
    }

    @Bean
    Queue paymentDeadLetterQueue() {
        return QueueBuilder.durable(PAYMENT_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding paymentDeadLetterBinding(Queue paymentDeadLetterQueue,
                                     DirectExchange paymentDeadLetterExchange) {
        return BindingBuilder.bind(paymentDeadLetterQueue)
                .to(paymentDeadLetterExchange)
                .with(PAYMENT_DEAD_LETTER_KEY);
    }

    @Bean
    DirectExchange refundCommandExchange() {
        return new DirectExchange(REFUND_COMMAND_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange refundDeadLetterExchange() {
        return new DirectExchange(REFUND_DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue refundRequestQueue() {
        return QueueBuilder.durable(REFUND_REQUEST_QUEUE)
                .deadLetterExchange(REFUND_DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(REFUND_DEAD_LETTER_KEY)
                .build();
    }

    @Bean
    Binding refundRequestBinding(Queue refundRequestQueue, DirectExchange refundCommandExchange) {
        return BindingBuilder.bind(refundRequestQueue).to(refundCommandExchange).with(REFUND_REQUEST_KEY);
    }

    @Bean
    Queue refundDeadLetterQueue() {
        return QueueBuilder.durable(REFUND_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding refundDeadLetterBinding(Queue refundDeadLetterQueue, DirectExchange refundDeadLetterExchange) {
        return BindingBuilder.bind(refundDeadLetterQueue)
                .to(refundDeadLetterExchange).with(REFUND_DEAD_LETTER_KEY);
    }
}
