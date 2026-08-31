package com.jarvis.commerce.payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentResponse(
        Long id,
        String paymentNo,
        Long orderId,
        BigDecimal amount,
        PaymentStatus status,
        String externalTransactionNo,
        String failureReason,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime expiresAt
) {
    static PaymentResponse from(PaymentOrder payment) {
        return new PaymentResponse(payment.getId(), payment.getPaymentNo(), payment.getOrder().getId(),
                payment.getAmount(), payment.getStatus(), payment.getExternalTransactionNo(),
                payment.getFailureReason(), payment.getVersion(), payment.getCreatedAt(), payment.getUpdatedAt(),
                payment.getExpiresAt());
    }
}
