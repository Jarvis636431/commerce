package com.jarvis.commerce.refund;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundOrderRepository extends JpaRepository<RefundOrder, Long> {
    Optional<RefundOrder> findByRefundNo(String refundNo);
    Optional<RefundOrder> findByRefundNoAndPayment_Order_UserId(String refundNo, Long userId);
    Optional<RefundOrder> findByIdempotencyKey(String idempotencyKey);
    List<RefundOrder> findAllByPaymentIdOrderByIdAsc(Long paymentId);
    List<RefundOrder> findAllByPayment_Order_IdOrderByIdAsc(Long orderId);
    List<RefundOrder> findAllByPayment_Order_IdAndPayment_Order_UserIdOrderByIdAsc(Long orderId, Long userId);
    boolean existsByPaymentIdAndStatusIn(Long paymentId, Collection<RefundStatus> statuses);
    @Query("select coalesce(sum(r.amount), 0) from RefundOrder r " +
            "where r.payment.id = :paymentId and r.status in :statuses")
    BigDecimal sumAmountByPaymentIdAndStatuses(@Param("paymentId") Long paymentId,
                                               @Param("statuses") Collection<RefundStatus> statuses);
    long countByStatus(RefundStatus status);
}
