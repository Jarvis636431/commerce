package com.jarvis.commerce.refund;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundOrderRepository extends JpaRepository<RefundOrder, Long> {
    Optional<RefundOrder> findByRefundNo(String refundNo);
    Optional<RefundOrder> findByRefundNoAndPayment_Order_UserId(String refundNo, Long userId);
    Optional<RefundOrder> findByIdempotencyKey(String idempotencyKey);
    Optional<RefundOrder> findByPaymentId(Long paymentId);
    long countByStatus(RefundStatus status);
}
