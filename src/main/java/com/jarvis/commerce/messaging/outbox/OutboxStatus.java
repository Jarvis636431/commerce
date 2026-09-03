package com.jarvis.commerce.messaging.outbox;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    RETRY,
    SENT,
    FAILED
}
