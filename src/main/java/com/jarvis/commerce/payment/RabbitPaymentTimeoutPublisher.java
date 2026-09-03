package com.jarvis.commerce.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
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
        CorrelationData correlation = new CorrelationData(eventId);
        try {
            rabbitTemplate.convertAndSend(PAYMENT_COMMAND_EXCHANGE, PAYMENT_TIMEOUT_SCHEDULE_KEY, payload, message -> {
                message.getMessageProperties().setMessageId(eventId);
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            }, correlation);
            correlation.getFuture().whenComplete((confirm, failure) ->
                    handleConfirmation(correlation, paymentNo, eventId, confirm, failure));
            log.info("Submitted payment timeout message: paymentNo={}, eventId={}, expiresAt={}",
                    paymentNo, eventId, expiresAt);
        } catch (RuntimeException exception) {
            log.error("Failed to schedule payment timeout message: paymentNo={}, eventId={}; "
                    + "database timeout scanner will compensate", paymentNo, eventId, exception);
        }
    }

    private void handleConfirmation(CorrelationData correlation, String paymentNo, String eventId,
                                    CorrelationData.Confirm confirm, Throwable failure) {
        if (failure != null) {
            log.error("Payment timeout publisher confirm failed: paymentNo={}, eventId={}",
                    paymentNo, eventId, failure);
            return;
        }
        if (!confirm.ack()) {
            log.error("RabbitMQ rejected payment timeout message: paymentNo={}, eventId={}, reason={}",
                    paymentNo, eventId, confirm.reason());
            return;
        }
        if (correlation.getReturned() != null) {
            var returned = correlation.getReturned();
            log.error("Payment timeout message was returned as unroutable: paymentNo={}, eventId={}, "
                            + "exchange={}, routingKey={}, replyCode={}, replyText={}",
                    paymentNo, eventId, returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText());
            return;
        }
        log.info("RabbitMQ confirmed payment timeout message: paymentNo={}, eventId={}", paymentNo, eventId);
    }
}
