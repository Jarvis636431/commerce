package com.jarvis.commerce.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.jarvis.commerce.messaging.RabbitTopology.PAYMENT_COMMAND_EXCHANGE;
import static com.jarvis.commerce.messaging.RabbitTopology.PAYMENT_TIMEOUT_SCHEDULE_KEY;

@Component
@ConditionalOnProperty(name = "commerce.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitPaymentTimeoutPublisher implements PaymentTimeoutPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitPaymentTimeoutPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    public RabbitPaymentTimeoutPublisher(RabbitTemplate rabbitTemplate, Clock clock) {
        this.rabbitTemplate = rabbitTemplate;
        this.clock = clock;
    }

    @Override
    public void publishAfterCommit(String paymentNo, OffsetDateTime expiresAt) {
        Runnable publish = () -> publish(paymentNo, expiresAt);
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            publish.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish.run();
            }
        });
    }

    private void publish(String paymentNo, OffsetDateTime expiresAt) {
        String eventId = UUID.randomUUID().toString();
        PaymentTimeoutMessage payload = new PaymentTimeoutMessage(
                eventId, paymentNo, expiresAt, OffsetDateTime.now(clock));
        try {
            rabbitTemplate.convertAndSend(PAYMENT_COMMAND_EXCHANGE, PAYMENT_TIMEOUT_SCHEDULE_KEY, payload, message -> {
                message.getMessageProperties().setMessageId(eventId);
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            });
            log.info("Scheduled payment timeout message: paymentNo={}, eventId={}, expiresAt={}",
                    paymentNo, eventId, expiresAt);
        } catch (RuntimeException exception) {
            log.error("Failed to schedule payment timeout message: paymentNo={}, eventId={}; "
                    + "database timeout scanner will compensate", paymentNo, eventId, exception);
        }
    }
}
