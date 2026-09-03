package com.jarvis.commerce.messaging.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.OffsetDateTime;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected OutboxEvent() {
    }

    public OutboxEvent(String eventId, String aggregateType, String aggregateId,
                       String eventType, String payload, OffsetDateTime now) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void claim(OffsetDateTime now, Duration lease) {
        status = OutboxStatus.PROCESSING;
        attempts++;
        lockedUntil = now.plus(lease);
        updatedAt = now;
    }

    public void markSent(OffsetDateTime now) {
        status = OutboxStatus.SENT;
        sentAt = now;
        lockedUntil = null;
        lastError = null;
        updatedAt = now;
    }

    public void markDeliveryFailed(String error, OffsetDateTime now, OffsetDateTime retryAt,
                                   int maxAttempts) {
        status = attempts >= maxAttempts ? OutboxStatus.FAILED : OutboxStatus.RETRY;
        nextAttemptAt = retryAt;
        lockedUntil = null;
        lastError = abbreviate(error, 1000);
        updatedAt = now;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return "Unknown delivery error";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public OffsetDateTime getLockedUntil() { return lockedUntil; }
    public String getLastError() { return lastError; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getSentAt() { return sentAt; }
    public long getVersion() { return version; }
}
