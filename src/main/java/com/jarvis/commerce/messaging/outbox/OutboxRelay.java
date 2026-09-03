package com.jarvis.commerce.messaging.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "commerce.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventService outboxService;
    private final RabbitOutboxPublisher publisher;
    private final int batchSize;
    private final Duration lease;

    public OutboxRelay(OutboxEventService outboxService, RabbitOutboxPublisher publisher,
                       @Value("${commerce.messaging.outbox.batch-size:50}") int batchSize,
                       @Value("${commerce.messaging.outbox.lease:PT30S}") Duration lease) {
        this.outboxService = outboxService;
        this.publisher = publisher;
        this.batchSize = batchSize;
        this.lease = lease;
    }

    @Scheduled(fixedDelayString = "${commerce.messaging.outbox.poll-interval:1000}")
    public void relay() {
        for (ClaimedOutboxEvent event : outboxService.claimBatch(batchSize, lease)) {
            try {
                publisher.publishAndWaitForConfirm(event);
                outboxService.markSent(event.eventId());
                log.info("Outbox event delivered: eventId={}, eventType={}, attempt={}",
                        event.eventId(), event.eventType(), event.attempt());
            } catch (RuntimeException exception) {
                outboxService.markDeliveryFailed(event.eventId(), exception);
                log.error("Outbox event delivery failed: eventId={}, eventType={}, attempt={}",
                        event.eventId(), event.eventType(), event.attempt(), exception);
            }
        }
    }
}
