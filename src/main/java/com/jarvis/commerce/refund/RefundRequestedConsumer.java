package com.jarvis.commerce.refund;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static com.jarvis.commerce.messaging.RabbitTopology.REFUND_REQUEST_QUEUE;

@Component
@ConditionalOnProperty(name = "commerce.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class RefundRequestedConsumer {
    private static final Logger log = LoggerFactory.getLogger(RefundRequestedConsumer.class);
    private final RefundService refundService;
    private final RefundGateway gateway;

    public RefundRequestedConsumer(RefundService refundService, RefundGateway gateway) {
        this.refundService = refundService;
        this.gateway = gateway;
    }

    @RabbitListener(queues = REFUND_REQUEST_QUEUE)
    public void consume(RefundRequestedMessage message) {
        RefundSubmission submission = refundService.requireSubmission(message.refundNo());
        if (submission == null) {
            log.info("Skipping terminal refund request: refundNo={}, eventId={}",
                    message.refundNo(), message.eventId());
            return;
        }
        gateway.submit(submission);
        refundService.markProcessing(message.refundNo());
        log.info("Submitted refund request to channel: refundNo={}, eventId={}",
                message.refundNo(), message.eventId());
    }
}
