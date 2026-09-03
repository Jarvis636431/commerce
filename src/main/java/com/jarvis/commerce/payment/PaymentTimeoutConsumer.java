package com.jarvis.commerce.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;

import static com.jarvis.commerce.messaging.RabbitTopology.PAYMENT_TIMEOUT_QUEUE;

@Component
@ConditionalOnProperty(name = "commerce.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentTimeoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentTimeoutConsumer.class);

    private final PaymentService paymentService;
    private final Clock clock;
    private final PaymentTimeoutMetrics metrics;

    public PaymentTimeoutConsumer(PaymentService paymentService, Clock clock, PaymentTimeoutMetrics metrics) {
        this.paymentService = paymentService;
        this.clock = clock;
        this.metrics = metrics;
    }

    @RabbitListener(queues = PAYMENT_TIMEOUT_QUEUE)
    public void consume(PaymentTimeoutMessage payload) {
        log.info("Processing payment timeout message: paymentNo={}, eventId={}",
                payload.paymentNo(), payload.eventId());
        try {
            boolean expired = paymentService.expireIfDue(payload.paymentNo(), OffsetDateTime.now(clock));
            if (expired) metrics.recordExpired(); else metrics.recordSkipped();
            log.info("Handled payment timeout message: paymentNo={}, eventId={}, expired={}",
                    payload.paymentNo(), payload.eventId(), expired);
        } catch (RuntimeException exception) {
            metrics.recordFailed();
            throw exception;
        }
    }
}
