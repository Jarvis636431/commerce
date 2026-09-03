package com.jarvis.commerce.messaging.outbox;

public final class OutboxEventTypes {
    public static final String PAYMENT_TIMEOUT_SCHEDULED = "payment.timeout.scheduled";
    public static final String REFUND_REQUESTED = "refund.requested";

    private OutboxEventTypes() {
    }
}
