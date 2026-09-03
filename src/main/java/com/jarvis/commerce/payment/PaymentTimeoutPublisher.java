package com.jarvis.commerce.payment;

public interface PaymentTimeoutPublisher {
    void publishAfterCommit(String paymentNo, java.time.OffsetDateTime expiresAt);
}
