package com.jarvis.commerce.payment;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;

import static com.jarvis.commerce.messaging.RabbitTopology.PAYMENT_TIMEOUT_QUEUE;

@Component
@ConditionalOnProperty(name = "commerce.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentTimeoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentTimeoutConsumer.class);

    private final PaymentService paymentService;
    private final Clock clock;

    public PaymentTimeoutConsumer(PaymentService paymentService, Clock clock) {
        this.paymentService = paymentService;
        this.clock = clock;
    }

    @RabbitListener(queues = PAYMENT_TIMEOUT_QUEUE)
    public void consume(PaymentTimeoutMessage payload, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            boolean expired = paymentService.expireIfDue(payload.paymentNo(), OffsetDateTime.now(clock));
            channel.basicAck(deliveryTag, false);
            log.info("Handled payment timeout message: paymentNo={}, eventId={}, expired={}",
                    payload.paymentNo(), payload.eventId(), expired);
        } catch (RuntimeException exception) {
            channel.basicNack(deliveryTag, false, false);
            log.error("Payment timeout message was rejected to dead-letter queue: paymentNo={}, eventId={}",
                    payload.paymentNo(), payload.eventId(), exception);
        }
    }
}
