package com.jarvis.commerce.messaging.outbox;

import com.jarvis.commerce.payment.PaymentTimeoutMessage;
import com.jarvis.commerce.refund.RefundRequestedMessage;
import com.jarvis.commerce.search.ProductIndexMessage;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

import static com.jarvis.commerce.messaging.RabbitTopology.PAYMENT_COMMAND_EXCHANGE;
import static com.jarvis.commerce.messaging.RabbitTopology.PAYMENT_TIMEOUT_SCHEDULE_KEY;
import static com.jarvis.commerce.messaging.RabbitTopology.REFUND_COMMAND_EXCHANGE;
import static com.jarvis.commerce.messaging.RabbitTopology.REFUND_REQUEST_KEY;
import static com.jarvis.commerce.messaging.RabbitTopology.SEARCH_COMMAND_EXCHANGE;
import static com.jarvis.commerce.messaging.RabbitTopology.PRODUCT_INDEX_KEY;
import static com.jarvis.commerce.messaging.outbox.OutboxEventTypes.PAYMENT_TIMEOUT_SCHEDULED;
import static com.jarvis.commerce.messaging.outbox.OutboxEventTypes.REFUND_REQUESTED;
import static com.jarvis.commerce.messaging.outbox.OutboxEventTypes.PRODUCT_INDEX_DELETE;
import static com.jarvis.commerce.messaging.outbox.OutboxEventTypes.PRODUCT_INDEX_UPSERT;

@Component
@ConditionalOnProperty(name = "commerce.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitOutboxPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration confirmTimeout;

    public RabbitOutboxPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper, Clock clock,
                                 @Value("${commerce.messaging.outbox.confirm-timeout:PT5S}")
                                 Duration confirmTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.confirmTimeout = confirmTimeout;
    }

    public void publishAndWaitForConfirm(ClaimedOutboxEvent event) {
        if (PAYMENT_TIMEOUT_SCHEDULED.equals(event.eventType())) {
            publishPaymentTimeout(event);
        } else if (REFUND_REQUESTED.equals(event.eventType())) {
            publishRefundRequest(event);
        } else if (PRODUCT_INDEX_UPSERT.equals(event.eventType())
                || PRODUCT_INDEX_DELETE.equals(event.eventType())) {
            publishProductIndex(event);
        } else {
            throw new IllegalArgumentException("Unsupported outbox event type: " + event.eventType());
        }
    }

    private void publishPaymentTimeout(ClaimedOutboxEvent event) {
        PaymentTimeoutMessage payload = objectMapper.readValue(event.payload(), PaymentTimeoutMessage.class);
        long remainingDelay = Math.max(0,
                Duration.between(OffsetDateTime.now(clock), payload.expiresAt()).toMillis());
        publish(event, PAYMENT_COMMAND_EXCHANGE, PAYMENT_TIMEOUT_SCHEDULE_KEY, payload, message -> {
            message.getMessageProperties().setMessageId(event.eventId());
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            message.getMessageProperties().setExpiration(Long.toString(remainingDelay));
            return message;
        });
    }

    private void publishRefundRequest(ClaimedOutboxEvent event) {
        RefundRequestedMessage payload = objectMapper.readValue(event.payload(), RefundRequestedMessage.class);
        publish(event, REFUND_COMMAND_EXCHANGE, REFUND_REQUEST_KEY, payload, message -> {
            message.getMessageProperties().setMessageId(event.eventId());
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return message;
        });
    }

    private void publishProductIndex(ClaimedOutboxEvent event) {
        ProductIndexMessage payload = objectMapper.readValue(event.payload(), ProductIndexMessage.class);
        publish(event, SEARCH_COMMAND_EXCHANGE, PRODUCT_INDEX_KEY, payload, message -> {
            message.getMessageProperties().setMessageId(event.eventId());
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return message;
        });
    }

    private void publish(ClaimedOutboxEvent event, String exchange, String routingKey, Object payload,
                         org.springframework.amqp.core.MessagePostProcessor postProcessor) {
        CorrelationData correlation = new CorrelationData(event.eventId());
        rabbitTemplate.convertAndSend(exchange, routingKey, payload, postProcessor, correlation);

        try {
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack()) {
                throw new IllegalStateException("RabbitMQ rejected message: " + confirm.reason());
            }
            if (correlation.getReturned() != null) {
                var returned = correlation.getReturned();
                throw new IllegalStateException("Message was unroutable: exchange=%s, routingKey=%s, reply=%s"
                        .formatted(returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for RabbitMQ confirm", exception);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException("RabbitMQ confirm was not received", exception);
        }
    }
}
