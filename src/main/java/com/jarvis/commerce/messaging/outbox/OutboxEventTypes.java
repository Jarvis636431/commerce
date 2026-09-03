package com.jarvis.commerce.messaging.outbox;

public final class OutboxEventTypes {
    public static final String PAYMENT_TIMEOUT_SCHEDULED = "payment.timeout.scheduled";
    public static final String REFUND_REQUESTED = "refund.requested";
    public static final String PRODUCT_INDEX_UPSERT = "product.index.upsert";
    public static final String PRODUCT_INDEX_DELETE = "product.index.delete";

    private OutboxEventTypes() {
    }
}
