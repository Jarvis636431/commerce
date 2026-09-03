package com.jarvis.commerce.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
@ConditionalOnProperty(name = "commerce.messaging.enabled", havingValue = "false")
public class NoOpPaymentTimeoutPublisher implements PaymentTimeoutPublisher {
    @Override
    public void publishAfterCommit(String paymentNo, OffsetDateTime expiresAt) {
        // Tests can exercise payment business rules without an external RabbitMQ broker.
    }
}
