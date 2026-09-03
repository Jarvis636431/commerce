package com.jarvis.commerce.messaging;

public final class RabbitTopology {

    public static final String PAYMENT_COMMAND_EXCHANGE = "commerce.payment.command";
    public static final String PAYMENT_TIMEOUT_DELAY_QUEUE = "commerce.payment.timeout.delay";
    public static final String PAYMENT_TIMEOUT_SCHEDULE_KEY = "payment.timeout.schedule";

    public static final String PAYMENT_EVENT_EXCHANGE = "commerce.payment.event";
    public static final String PAYMENT_TIMEOUT_QUEUE = "commerce.payment.timeout";
    public static final String PAYMENT_TIMEOUT_DUE_KEY = "payment.timeout.due";

    public static final String PAYMENT_DEAD_LETTER_EXCHANGE = "commerce.payment.dlx";
    public static final String PAYMENT_DEAD_LETTER_QUEUE = "commerce.payment.dead-letter";
    public static final String PAYMENT_DEAD_LETTER_KEY = "payment.dead-letter";

    private RabbitTopology() {
    }
}
