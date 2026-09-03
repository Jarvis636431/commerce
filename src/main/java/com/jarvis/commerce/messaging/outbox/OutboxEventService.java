package com.jarvis.commerce.messaging.outbox;

import com.jarvis.commerce.payment.PaymentTimeoutMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.jarvis.commerce.messaging.outbox.OutboxEventTypes.PAYMENT_TIMEOUT_SCHEDULED;

@Service
public class OutboxEventService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration retryInitialInterval;
    private final Duration retryMaxInterval;

    public OutboxEventService(OutboxEventRepository repository, ObjectMapper objectMapper, Clock clock,
                              @Value("${commerce.messaging.outbox.max-attempts:10}") int maxAttempts,
                              @Value("${commerce.messaging.outbox.retry-initial-interval:PT2S}")
                              Duration retryInitialInterval,
                              @Value("${commerce.messaging.outbox.retry-max-interval:PT5M}")
                              Duration retryMaxInterval) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.retryInitialInterval = retryInitialInterval;
        this.retryMaxInterval = retryMaxInterval;
    }

    @Transactional
    public void schedulePaymentTimeout(String paymentNo, OffsetDateTime expiresAt) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        String eventId = UUID.randomUUID().toString();
        PaymentTimeoutMessage message = new PaymentTimeoutMessage(eventId, paymentNo, expiresAt, now);
        repository.save(new OutboxEvent(eventId, "PAYMENT", paymentNo,
                PAYMENT_TIMEOUT_SCHEDULED, objectMapper.writeValueAsString(message), now));
    }

    @Transactional
    public List<ClaimedOutboxEvent> claimBatch(int batchSize, Duration lease) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<OutboxEvent> events = repository.findClaimable(
                List.of(OutboxStatus.PENDING, OutboxStatus.RETRY), OutboxStatus.PROCESSING,
                now, PageRequest.of(0, batchSize));
        events.forEach(event -> event.claim(now, lease));
        repository.flush();
        return events.stream().map(ClaimedOutboxEvent::from).toList();
    }

    @Transactional
    public void markSent(String eventId) {
        OutboxEvent event = requireEvent(eventId);
        if (event.getStatus() != OutboxStatus.PROCESSING) return;
        event.markSent(OffsetDateTime.now(clock));
    }

    @Transactional
    public void markDeliveryFailed(String eventId, Throwable failure) {
        OutboxEvent event = requireEvent(eventId);
        if (event.getStatus() != OutboxStatus.PROCESSING) return;
        OffsetDateTime now = OffsetDateTime.now(clock);
        event.markDeliveryFailed(failureMessage(failure), now, now.plus(retryDelay(event.getAttempts())), maxAttempts);
    }

    private Duration retryDelay(int attempt) {
        long factor = 1L << Math.min(Math.max(attempt - 1, 0), 20);
        Duration calculated = retryInitialInterval.multipliedBy(factor);
        return calculated.compareTo(retryMaxInterval) > 0 ? retryMaxInterval : calculated;
    }

    private String failureMessage(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private OutboxEvent requireEvent(String eventId) {
        return repository.findByEventId(eventId)
                .orElseThrow(() -> new IllegalStateException("Outbox event was not found: " + eventId));
    }
}
