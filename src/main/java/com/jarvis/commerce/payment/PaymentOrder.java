package com.jarvis.commerce.payment;

import com.jarvis.commerce.common.ConflictException;
import com.jarvis.commerce.order.CustomerOrder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "payment_order")
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_no", nullable = false, unique = true, length = 40)
    private String paymentNo;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private CustomerOrder order;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "external_transaction_no", unique = true, length = 100)
    private String externalTransactionNo;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    protected PaymentOrder() {
    }

    public PaymentOrder(String paymentNo, CustomerOrder order, String idempotencyKey, BigDecimal amount,
                        OffsetDateTime expiresAt) {
        this.paymentNo = paymentNo;
        this.order = order;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
        this.expiresAt = expiresAt;
    }

    public void markSuccess(String externalTransactionNo) {
        requirePending("Only pending payments can succeed");
        this.externalTransactionNo = externalTransactionNo;
        this.failureReason = null;
        this.status = PaymentStatus.SUCCESS;
    }

    public void markFailed(String reason) {
        requirePending("Only pending payments can fail");
        this.failureReason = reason;
        this.status = PaymentStatus.FAILED;
    }

    public void close() {
        requirePending("Only pending payments can be closed");
        this.status = PaymentStatus.CLOSED;
    }

    public void retry(OffsetDateTime newExpiresAt) {
        if (status != PaymentStatus.FAILED) {
            throw new ConflictException("Only failed payments can be retried");
        }
        this.status = PaymentStatus.PENDING;
        this.failureReason = null;
        this.expiresAt = newExpiresAt;
    }

    public boolean isExpiredAt(OffsetDateTime time) {
        return !expiresAt.isAfter(time);
    }

    private void requirePending(String message) {
        if (status != PaymentStatus.PENDING) {
            throw new ConflictException(message);
        }
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public String getPaymentNo() { return paymentNo; }
    public CustomerOrder getOrder() { return order; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public BigDecimal getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getExternalTransactionNo() { return externalTransactionNo; }
    public String getFailureReason() { return failureReason; }
    public long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
}
