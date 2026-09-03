package com.jarvis.commerce.payment;

import java.time.OffsetDateTime;

public record PaymentTimeoutMessage(
        String eventId,
        String paymentNo,
        OffsetDateTime expiresAt,
        OffsetDateTime occurredAt) {
}
