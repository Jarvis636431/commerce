package com.jarvis.commerce.messaging.outbox;

import com.jarvis.commerce.payment.PaymentTimeoutMessage;
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
import static com.jarvis.commerce.messaging.outbox.OutboxEventTypes.PAYMENT_TIMEOUT_SCHEDULED;

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
        if (!PAYMENT_TIMEOUT_SCHEDULED.equals(event.eventType())) {
            throw new IllegalArgumentException("Unsupported outbox event type: " + event.eventType());
        }
        PaymentTimeoutMessage payload = objectMapper.readValue(event.payload(), PaymentTimeoutMessage.class);
        CorrelationData correlation = new CorrelationData(event.eventId());
        long remainingDelay = Math.max(0,
                Duration.between(OffsetDateTime.now(clock), payload.expiresAt()).toMillis());

        rabbitTemplate.convertAndSend(PAYMENT_COMMAND_EXCHANGE, PAYMENT_TIMEOUT_SCHEDULE_KEY, payload, message -> {
            message.getMessageProperties().setMessageId(event.eventId());
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            message.getMessageProperties().setExpiration(Long.toString(remainingDelay));
            return message;
        }, correlation);

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
