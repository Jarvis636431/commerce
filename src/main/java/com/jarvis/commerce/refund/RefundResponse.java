package com.jarvis.commerce.refund;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RefundResponse(
        Long id, String refundNo, Long paymentId, Long orderId, BigDecimal amount,
        String reason, RefundStatus status, String externalRefundNo, String failureReason,
        long version, OffsetDateTime createdAt, OffsetDateTime updatedAt
) {
    static RefundResponse from(RefundOrder refund) {
        return new RefundResponse(refund.getId(), refund.getRefundNo(), refund.getPayment().getId(),
                refund.getPayment().getOrder().getId(), refund.getAmount(), refund.getReason(), refund.getStatus(),
                refund.getExternalRefundNo(), refund.getFailureReason(), refund.getVersion(),
                refund.getCreatedAt(), refund.getUpdatedAt());
    }
}
