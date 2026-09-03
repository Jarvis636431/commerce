package com.jarvis.commerce.refund;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "refund_notification")
public class RefundNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_id", nullable = false)
    private RefundOrder refund;

    @Column(name = "notification_id", nullable = false, unique = true, length = 100)
    private String notificationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected RefundNotification() {}

    public RefundNotification(RefundOrder refund, String notificationId) {
        this.refund = refund;
        this.notificationId = notificationId;
    }

    @PrePersist
    void onCreate() { createdAt = OffsetDateTime.now(ZoneOffset.UTC); }

    public Long getRefundId() { return refund.getId(); }
}
