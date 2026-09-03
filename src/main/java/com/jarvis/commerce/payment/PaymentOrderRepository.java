package com.jarvis.commerce.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    Optional<PaymentOrder> findByPaymentNo(String paymentNo);
    Optional<PaymentOrder> findByPaymentNoAndOrderUserId(String paymentNo, Long userId);
    Optional<PaymentOrder> findByIdempotencyKey(String idempotencyKey);
    Optional<PaymentOrder> findByOrderId(Long orderId);
    List<PaymentOrder> findTop100ByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
            PaymentStatus status, OffsetDateTime expiresAt);
}
