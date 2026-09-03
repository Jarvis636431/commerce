package com.jarvis.commerce.refund;

import com.jarvis.commerce.common.ConflictException;
import com.jarvis.commerce.order.OrderStatus;
import com.jarvis.commerce.payment.PaymentOrder;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "refund_order")
public class RefundOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refund_no", nullable = false, unique = true, length = 40)
    private String refundNo;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private PaymentOrder payment;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status_before_refund", nullable = false, length = 30)
    private OrderStatus orderStatusBeforeRefund;

    @Column(name = "external_refund_no", unique = true, length = 100)
    private String externalRefundNo;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected RefundOrder() {}

    public RefundOrder(String refundNo, PaymentOrder payment, String idempotencyKey,
                       BigDecimal amount, String reason, OrderStatus orderStatusBeforeRefund) {
        this.refundNo = refundNo;
        this.payment = payment;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.reason = reason;
        this.orderStatusBeforeRefund = orderStatusBeforeRefund;
        this.status = RefundStatus.PENDING;
    }

    public void markSuccess(String externalRefundNo) {
        requirePending();
        this.externalRefundNo = externalRefundNo;
        this.failureReason = null;
        this.status = RefundStatus.SUCCESS;
    }

    public void markFailed(String failureReason) {
        requirePending();
        this.failureReason = failureReason;
        this.status = RefundStatus.FAILED;
    }

    private void requirePending() {
        if (status != RefundStatus.PENDING) {
            throw new ConflictException("Only pending refunds can be completed");
        }
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(ZoneOffset.UTC); }

    public Long getId() { return id; }
    public String getRefundNo() { return refundNo; }
    public PaymentOrder getPayment() { return payment; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public BigDecimal getAmount() { return amount; }
    public String getReason() { return reason; }
    public RefundStatus getStatus() { return status; }
    public OrderStatus getOrderStatusBeforeRefund() { return orderStatusBeforeRefund; }
    public String getExternalRefundNo() { return externalRefundNo; }
    public String getFailureReason() { return failureReason; }
    public long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
