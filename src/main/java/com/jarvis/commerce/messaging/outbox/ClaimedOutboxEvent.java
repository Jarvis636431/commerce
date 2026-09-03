package com.jarvis.commerce.messaging.outbox;

public record ClaimedOutboxEvent(
        String eventId,
        String aggregateId,
        String eventType,
        String payload,
        int attempt) {

    static ClaimedOutboxEvent from(OutboxEvent event) {
        return new ClaimedOutboxEvent(event.getEventId(), event.getAggregateId(), event.getEventType(),
                event.getPayload(), event.getAttempts());
    }
}
