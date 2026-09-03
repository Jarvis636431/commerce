package com.jarvis.commerce.order;

import com.jarvis.commerce.common.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "customer_order")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 40)
    private String orderNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "receiver_name", length = 100)
    private String receiverName;

    @Column(name = "receiver_phone", length = 32)
    private String receiverPhone;

    @Column(length = 100)
    private String province;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String district;

    @Column(name = "detail_address", length = 500)
    private String detailAddress;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected CustomerOrder() {
    }

    public CustomerOrder(String orderNo, BigDecimal totalAmount, Long userId,
                         String receiverName, String receiverPhone, String province, String city,
                         String district, String detailAddress, String postalCode) {
        this.orderNo = orderNo;
        this.totalAmount = totalAmount;
        this.userId = userId;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.province = province;
        this.city = city;
        this.district = district;
        this.detailAddress = detailAddress;
        this.postalCode = postalCode;
        this.status = OrderStatus.PENDING_PAYMENT;
    }

    public void markPaid() {
        requireStatus(OrderStatus.PENDING_PAYMENT, "Only pending orders can be paid");
        status = OrderStatus.PAID;
    }

    public void cancel() {
        requireStatus(OrderStatus.PENDING_PAYMENT, "Only pending orders can be cancelled");
        status = OrderStatus.CANCELLED;
    }

    public void complete() {
        requireStatus(OrderStatus.PAID, "Only paid orders can be completed");
        status = OrderStatus.COMPLETED;
    }

    public OrderStatus beginRefund() {
        if (status != OrderStatus.PAID && status != OrderStatus.COMPLETED) {
            throw new ConflictException("Only paid or completed orders can be refunded");
        }
        OrderStatus previousStatus = status;
        status = OrderStatus.REFUNDING;
        return previousStatus;
    }

    public void markRefunded() {
        requireStatus(OrderStatus.REFUNDING, "Only refunding orders can be marked refunded");
        status = OrderStatus.REFUNDED;
    }

    public void restoreAfterRefundFailure(OrderStatus previousStatus) {
        requireStatus(OrderStatus.REFUNDING, "Only refunding orders can restore their status");
        if (previousStatus != OrderStatus.PAID && previousStatus != OrderStatus.COMPLETED) {
            throw new IllegalArgumentException("Invalid status before refund: " + previousStatus);
        }
        status = previousStatus;
    }

    public void markPartiallyRefunded(OrderStatus previousStatus) {
        restoreAfterRefundFailure(previousStatus);
    }

    private void requireStatus(OrderStatus expected, String message) {
        if (status != expected) {
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
    public String getOrderNo() { return orderNo; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Long getUserId() { return userId; }
    public String getReceiverName() { return receiverName; }
    public String getReceiverPhone() { return receiverPhone; }
    public String getProvince() { return province; }
    public String getCity() { return city; }
    public String getDistrict() { return district; }
    public String getDetailAddress() { return detailAddress; }
    public String getPostalCode() { return postalCode; }
    public long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
